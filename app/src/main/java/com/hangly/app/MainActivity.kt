package com.hangly.app

import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.hangly.app.adapter.CharmPresetAdapter
import com.hangly.app.data.HanglyPreferences
import com.hangly.app.databinding.ActivityMainBinding
import com.hangly.app.databinding.DialogCropCharmBinding
import com.hangly.app.service.HanglyOverlayService
import com.hangly.app.utils.ImageCutoutHelper
import com.hangly.app.utils.StatusBarHelper
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: HanglyPreferences
    private lateinit var charmAdapter: CharmPresetAdapter

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { uriVal ->
                try {
                    contentResolver.openInputStream(uriVal)?.use { inputStream ->
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        if (bitmap != null) {
                            showCropShapeDialog(bitmap)
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Could not open photo: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = HanglyPreferences(this)
        applyAppTheme(prefs.themeMode)

        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupUI()
        setupListeners()
        updatePermissionsCard()
        syncAllViewsWithPreferences()

        if (prefs.isEnabled && Settings.canDrawOverlays(this)) {
            HanglyOverlayService.startService(this)
        }
    }

    private fun applyAppTheme(mode: Int) {
        val nightMode = when (mode) {
            HanglyPreferences.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            HanglyPreferences.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    private fun updateThemeButtonIcon(mode: Int) {
        binding.btnToggleTheme.text = when (mode) {
            HanglyPreferences.THEME_LIGHT -> "☀️"
            HanglyPreferences.THEME_DARK -> "🌙"
            else -> "🌓"
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionsCard()
        syncAllViewsWithPreferences()
    }

    private fun setupUI() {
        charmAdapter = CharmPresetAdapter(prefs.selectedCharm) { charm ->
            prefs.selectedCharm = charm.id
        }
        binding.recyclerCharms.adapter = charmAdapter
    }

    private fun setupListeners() {
        updateThemeButtonIcon(prefs.themeMode)

        binding.btnToggleTheme.setOnClickListener {
            val nextMode = when (prefs.themeMode) {
                HanglyPreferences.THEME_SYSTEM -> HanglyPreferences.THEME_LIGHT
                HanglyPreferences.THEME_LIGHT -> HanglyPreferences.THEME_DARK
                else -> HanglyPreferences.THEME_SYSTEM
            }
            prefs.themeMode = nextMode
            applyAppTheme(nextMode)
            updateThemeButtonIcon(nextMode)
            val name = when (nextMode) {
                HanglyPreferences.THEME_LIGHT -> "Light Theme enabled"
                HanglyPreferences.THEME_DARK -> "Dark Theme enabled"
                else -> "System Theme enabled"
            }
            Toast.makeText(this, name, Toast.LENGTH_SHORT).show()
        }

        binding.switchMasterToggle.setOnCheckedChangeListener { _, isChecked ->
            prefs.isEnabled = isChecked
            if (isChecked) {
                if (checkOrRequestOverlayPermission()) {
                    HanglyOverlayService.startService(this)
                    updateStatusText(true)
                } else {
                    binding.switchMasterToggle.isChecked = false
                }
            } else {
                HanglyOverlayService.stopService(this)
                updateStatusText(false)
            }
        }

        binding.btnGrantOverlay.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        binding.btnGrantAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        binding.btnPickCustomPhoto.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.btnCropCurrentCharm.setOnClickListener {
            val charmBmp = getCharmBitmap(prefs.selectedCharm)
            if (charmBmp != null) {
                showCropShapeDialog(charmBmp)
            } else {
                Toast.makeText(this, "Select or pick a charm first", Toast.LENGTH_SHORT).show()
            }
        }

        // Rope Style Buttons
        val styleButtons = listOf(
            binding.btnStyleClassic to HanglyPreferences.ROPE_STYLE_CLASSIC,
            binding.btnStyleChain to HanglyPreferences.ROPE_STYLE_CHAIN,
            binding.btnStyleWeb to HanglyPreferences.ROPE_STYLE_WEB,
            binding.btnStyleBeads to HanglyPreferences.ROPE_STYLE_BEADS,
            binding.btnStyleRibbon to HanglyPreferences.ROPE_STYLE_BRAIDED
        )
        for ((btn, style) in styleButtons) {
            btn.setOnClickListener {
                prefs.ropeStyle = style
                updateRopeStyleUI()
            }
        }

        // Connector Style Buttons
        binding.btnConnectorCrescent.setOnClickListener {
            prefs.connectorStyle = HanglyPreferences.CONNECTOR_CRESCENT
            updateConnectorUI()
        }
        binding.btnConnectorRing.setOnClickListener {
            prefs.connectorStyle = HanglyPreferences.CONNECTOR_RING
            updateConnectorUI()
        }

        // Sliders
        binding.sliderPosition.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val percent = value / 100f
                prefs.horizontalPercent = percent
                updatePositionLabel(value.toInt())
            }
        }

        binding.btnPosLeft.setOnClickListener { updatePosition(20f) }
        binding.btnPosCenter.setOnClickListener { updatePosition(50f) }
        binding.btnPosRight.setOnClickListener { updatePosition(80f) }

        binding.sliderRopeLength.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                prefs.ropeLengthDp = value
                binding.txtRopeLengthValue.text = "${value.toInt()} dp"
            }
        }

        binding.sliderRopeThickness.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                prefs.ropeThicknessDp = value
                binding.txtRopeThicknessValue.text = "${value.toInt()} dp"
            }
        }

        binding.sliderCharmSize.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                prefs.charmSizeDp = value
                binding.txtCharmSizeValue.text = "${value.toInt()} dp"
            }
        }

        binding.sliderGravity.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                prefs.gravityPercent = value
                binding.txtGravityValue.text = "${value.toInt()}% (${String.format("%.2f", value / 100f)}g)"
            }
        }

        binding.switchTiltPhysics.setOnCheckedChangeListener { _, isChecked ->
            prefs.isTiltPhysicsEnabled = isChecked
        }

        val colors = listOf(
            binding.colorBlack to Color.parseColor("#212121"),
            binding.colorRed to Color.parseColor("#E53935"),
            binding.colorGold to Color.parseColor("#FFD700"),
            binding.colorWhite to Color.parseColor("#F5F5F5"),
            binding.colorCyan to Color.parseColor("#00E5FF"),
            binding.colorNeonGreen to Color.parseColor("#39FF14"),
            binding.colorMagenta to Color.parseColor("#FF007F")
        )
        for ((view, color) in colors) {
            view.setOnClickListener {
                prefs.ropeColor = color
            }
        }

        binding.btnTestNotifications.setOnClickListener {
            val opened = StatusBarHelper.openNotifications(this)
            if (opened) {
                Toast.makeText(this, "Notifications opened", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnTestQuickSettings.setOnClickListener {
            val opened = StatusBarHelper.openQuickSettings(this)
            if (opened) {
                Toast.makeText(this, "Quick Settings opened", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnResetDefaults.setOnClickListener {
            prefs.resetToDefaults()
            charmAdapter.setSelected(prefs.selectedCharm)
            syncAllViewsWithPreferences()
            Toast.makeText(this, "Reset to Sangly defaults (75% gravity)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getCharmBitmap(charmId: String): Bitmap? {
        if (charmId == HanglyPreferences.CHARM_CUSTOM && !prefs.customImagePath.isNullOrEmpty()) {
            val file = File(prefs.customImagePath!!)
            if (file.exists()) {
                val bmp = BitmapFactory.decodeFile(file.absolutePath)
                if (bmp != null) return bmp
            }
        }
        val resId = when (charmId) {
            HanglyPreferences.CHARM_SPIDERMAN -> R.drawable.ic_spiderman
            HanglyPreferences.CHARM_EVIL_EYE -> R.drawable.ic_evil_eye
            HanglyPreferences.CHARM_CAP_SHIELD -> R.drawable.ic_cap_shield
            HanglyPreferences.CHARM_LUCKY_CAT -> R.drawable.ic_lucky_cat
            HanglyPreferences.CHARM_GHOST -> R.drawable.ic_ghost
            else -> R.drawable.ic_spiderman
        }
        val drawable = ContextCompat.getDrawable(this, resId) ?: return null
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, 512, 512)
        drawable.draw(canvas)
        return bitmap
    }

    private fun showCropShapeDialog(sourceBitmap: Bitmap) {
        val dialog = Dialog(this)
        val dialogBinding = DialogCropCharmBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.94).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        var zoom = 1.0f
        var panX = 0f
        var panY = 0f

        fun renderPreview() {
            val preview = ImageCutoutHelper.createTransformedBitmap(
                sourceBitmap, 400, panX, panY, zoom
            )
            dialogBinding.imgCropPreview.setImageBitmap(preview)
            dialogBinding.txtZoomStatus.text = String.format("Scale: %.2fx", zoom)
        }

        renderPreview()

        val scaleDetector = ScaleGestureDetector(
            this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    zoom = (zoom * detector.scaleFactor).coerceIn(0.2f, 6.0f)
                    renderPreview()
                    return true
                }
            }
        )

        var lastTouchX = 0f
        var lastTouchY = 0f

        dialogBinding.imgCropPreview.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchX = event.x
                    lastTouchY = event.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!scaleDetector.isInProgress) {
                        val dx = event.x - lastTouchX
                        val dy = event.y - lastTouchY
                        panX += dx
                        panY += dy
                        lastTouchX = event.x
                        lastTouchY = event.y
                        renderPreview()
                    }
                    true
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    lastTouchX = event.x
                    lastTouchY = event.y
                    true
                }
                else -> true
            }
        }

        dialogBinding.btnResetTransform.setOnClickListener {
            zoom = 1.0f
            panX = 0f
            panY = 0f
            renderPreview()
        }

        dialogBinding.btnCancelCrop.setOnClickListener { dialog.dismiss() }

        dialogBinding.btnApplyCrop.setOnClickListener {
            // Render high-res transformed charm
            val finalBmp = ImageCutoutHelper.createTransformedBitmap(
                sourceBitmap, 512, panX, panY, zoom
            )
            val destFile = File(filesDir, "custom_charm.png")
            // Strictly save as PNG format regardless of whether source was JPG, JPEG, or WebP
            val saved = ImageCutoutHelper.saveAsPng(finalBmp, destFile)
            if (saved) {
                prefs.customImagePath = destFile.absolutePath
                prefs.selectedCharm = HanglyPreferences.CHARM_CUSTOM
                charmAdapter.setSelected(HanglyPreferences.CHARM_CUSTOM)
                Toast.makeText(this, "Character applied as PNG!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to save charm", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun updateRopeStyleUI() {
        val activeStyle = prefs.ropeStyle
        val buttons = mapOf(
            HanglyPreferences.ROPE_STYLE_CLASSIC to binding.btnStyleClassic,
            HanglyPreferences.ROPE_STYLE_CHAIN to binding.btnStyleChain,
            HanglyPreferences.ROPE_STYLE_WEB to binding.btnStyleWeb,
            HanglyPreferences.ROPE_STYLE_BEADS to binding.btnStyleBeads,
            HanglyPreferences.ROPE_STYLE_BRAIDED to binding.btnStyleRibbon,
            HanglyPreferences.ROPE_STYLE_RIBBON to binding.btnStyleRibbon
        )
        for ((style, btn) in buttons) {
            val isSelected = style == activeStyle
            btn.strokeColor = ContextCompat.getColorStateList(
                this,
                if (isSelected) R.color.secondary else R.color.divider_color
            )
            btn.strokeWidth = if (isSelected) 3 else 1
        }
    }

    private fun updateConnectorUI() {
        val isCrescent = prefs.connectorStyle == HanglyPreferences.CONNECTOR_CRESCENT
        binding.btnConnectorCrescent.strokeColor = ContextCompat.getColorStateList(
            this,
            if (isCrescent) R.color.secondary else R.color.divider_color
        )
        binding.btnConnectorCrescent.strokeWidth = if (isCrescent) 3 else 1

        binding.btnConnectorRing.strokeColor = ContextCompat.getColorStateList(
            this,
            if (!isCrescent) R.color.secondary else R.color.divider_color
        )
        binding.btnConnectorRing.strokeWidth = if (!isCrescent) 3 else 1
    }

    private fun updatePosition(percentValue: Float) {
        binding.sliderPosition.value = percentValue
        prefs.horizontalPercent = percentValue / 100f
        updatePositionLabel(percentValue.toInt())
    }

    private fun updatePositionLabel(percent: Int) {
        val label = when (percent) {
            in 0..35 -> "$percent% (Left side)"
            in 36..64 -> "$percent% (Center / Notch)"
            else -> "$percent% (Right side)"
        }
        binding.txtPositionValue.text = label
    }

    private fun updateStatusText(active: Boolean) {
        if (active) {
            binding.txtStatusSubtitle.text = getString(R.string.status_active)
            binding.txtStatusSubtitle.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
        } else {
            binding.txtStatusSubtitle.text = getString(R.string.status_inactive)
            binding.txtStatusSubtitle.setTextColor(ContextCompat.getColor(this, R.color.text_muted))
        }
    }

    private fun syncAllViewsWithPreferences() {
        val isOverlayOk = Settings.canDrawOverlays(this)
        val enabled = prefs.isEnabled && isOverlayOk

        binding.switchMasterToggle.isChecked = enabled
        updateStatusText(enabled)

        val posPercent = (prefs.horizontalPercent * 100).coerceIn(5f, 95f)
        binding.sliderPosition.value = posPercent
        updatePositionLabel(posPercent.toInt())

        binding.sliderRopeLength.value = prefs.ropeLengthDp.coerceIn(50f, 280f)
        binding.txtRopeLengthValue.text = "${prefs.ropeLengthDp.toInt()} dp"

        binding.sliderRopeThickness.value = prefs.ropeThicknessDp.coerceIn(1f, 12f)
        binding.txtRopeThicknessValue.text = "${prefs.ropeThicknessDp.toInt()} dp"

        binding.sliderCharmSize.value = prefs.charmSizeDp.coerceIn(32f, 120f)
        binding.txtCharmSizeValue.text = "${prefs.charmSizeDp.toInt()} dp"

        binding.sliderGravity.value = prefs.gravityPercent.coerceIn(25f, 150f)
        binding.txtGravityValue.text = "${prefs.gravityPercent.toInt()}% (${String.format("%.2f", prefs.gravityPercent / 100f)}g)"

        binding.switchTiltPhysics.isChecked = prefs.isTiltPhysicsEnabled

        updateRopeStyleUI()
        updateConnectorUI()
    }

    private fun updatePermissionsCard() {
        val overlayGranted = Settings.canDrawOverlays(this)
        val accessibilityServiceActive = StatusBarHelper.isAccessibilityServiceEnabled(this)

        if (overlayGranted) {
            binding.txtOverlayStatus.text = getString(R.string.overlay_perm_granted)
            binding.txtOverlayStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
            binding.btnGrantOverlay.visibility = View.GONE
        } else {
            binding.txtOverlayStatus.text = "Required to float above all apps"
            binding.txtOverlayStatus.setTextColor(ContextCompat.getColor(this, R.color.text_muted))
            binding.btnGrantOverlay.visibility = View.VISIBLE
        }

        // Quick settings & Notifications: Automatically allowed on every Android via EXPAND_STATUS_BAR!
        if (accessibilityServiceActive) {
            binding.txtAccessibilityStatus.text = "Active (Accessibility Service & System Controls)"
            binding.txtAccessibilityStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
            binding.btnGrantAccessibility.visibility = View.GONE
        } else {
            binding.txtAccessibilityStatus.text = "Automatically active on this device (System EXPAND_STATUS_BAR)"
            binding.txtAccessibilityStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
            binding.btnGrantAccessibility.visibility = View.GONE
        }

        binding.cardPermissions.visibility = if (overlayGranted) View.GONE else View.VISIBLE
    }

    private fun checkOrRequestOverlayPermission(): Boolean {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            Toast.makeText(this, "Please allow Display over other apps for Sangly", Toast.LENGTH_LONG).show()
            return false
        }
        return true
    }
}
