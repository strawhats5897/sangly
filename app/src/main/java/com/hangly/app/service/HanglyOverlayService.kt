package com.hangly.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.hangly.app.MainActivity
import com.hangly.app.R
import com.hangly.app.data.HanglyPreferences
import com.hangly.app.utils.StatusBarHelper
import com.hangly.app.view.HanglyPhysicsView
import kotlin.math.abs

/**
 * High-performance Dual-Window Foreground Overlay Service:
 *
 * 1. Render Window: Full-width visual overlay that renders realistic swinging ropes, web, and charm
 *    across the entire screen with FLAG_NOT_TOUCHABLE. Never clips on tilt or swing, and 100% passes
 *    touches straight to underlying apps.
 *
 * 2. Charm Touch Window: A small, invisible interactive square positioned exactly over the charm.
 *    It dynamically tracks and travels along with the charm as it tilts and swings, capturing user
 *    interactions (taps, drags, pull-downs) without ever blocking any other part of the screen.
 */
class HanglyOverlayService : Service(), SensorEventListener,
    SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "hangly_channel"

        fun startService(context: Context) {
            val intent = Intent(context, HanglyOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            context.stopService(Intent(context, HanglyOverlayService::class.java))
        }
    }

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: HanglyPreferences

    // Window 1: Visual Rendering (FLAG_NOT_TOUCHABLE)
    private var renderView: HanglyPhysicsView? = null
    private var renderLayoutParams: WindowManager.LayoutParams? = null

    // Window 2: Interactive Touch Target (Small square traveling with charm)
    private var touchTargetView: View? = null
    private var touchLayoutParams: WindowManager.LayoutParams? = null

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var density: Float = 1f

    private var lastReportedAx = 0f
    private var isScreenOn = true
    private var lastTouchTargetX = -1000
    private var lastTouchTargetY = -1000

    // Receiver to suspend all processing when screen is off
    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    unregisterSensorListener()
                    renderView?.stopAnimationLoop()
                }
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    if (prefs.isTiltPhysicsEnabled) {
                        registerSensorListener()
                    }
                    renderView?.wakeAndAnimate()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        density = resources.displayMetrics.density
        prefs = HanglyPreferences(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        createNotificationChannel()
        startAsForeground()

        setupOverlayViews()
        applyPreferences()
        prefs.registerListener(this)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenStateReceiver, filter)

        if (prefs.isTiltPhysicsEnabled) {
            registerSensorListener()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = getString(R.string.channel_description)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun startAsForeground() {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sangly Active")
            .setContentText("Interactive charm overlay • Pull down for actions")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun setupOverlayViews() {
        if (renderView != null) return

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val displayHeight = displayMetrics.heightPixels
        val renderHeight = (displayHeight * 0.75f).toInt()

        // 1. VISUAL RENDERING WINDOW (Full screen width, never clips charm, 100% touch pass-through)
        val rView = HanglyPhysicsView(this).apply {
            isInteractive = true
            allowAnchorDragging = true

            onPullTriggered = { isLeftSide ->
                if (isLeftSide) {
                    StatusBarHelper.openNotifications(this@HanglyOverlayService)
                } else {
                    StatusBarHelper.openQuickSettings(this@HanglyOverlayService)
                }
            }

            onAnchorPositionChanged = { newPercent ->
                prefs.horizontalPercent = newPercent
            }

            onCharmPositionChanged = { cx, cy ->
                updateTouchTargetPosition(cx, cy)
            }
        }
        renderView = rView

        val rParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            renderHeight,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            alpha = 1.0f
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        renderLayoutParams = rParams

        try {
            windowManager.addView(rView, rParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. INTERACTIVE CHARM TOUCH WINDOW (Small square placed directly over the charm, travels with charm)
        val touchWidth = (prefs.charmSizeDp * density + 16f * density).toInt()
        val touchHeight = (prefs.charmSizeDp * density + 24f * density).toInt()
        val initialCx = screenWidth * prefs.horizontalPercent
        val initialCy = prefs.ropeLengthDp * density

        val tView = View(this).apply {
            setOnTouchListener { _, event ->
                val rv = renderView ?: return@setOnTouchListener false
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        rv.handleCharmTouchDown(event.rawX, event.rawY)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        rv.handleCharmTouchMove(event.rawX, event.rawY)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        rv.handleCharmTouchUp(event.rawX, event.rawY)
                        true
                    }
                    else -> false
                }
            }
        }
        touchTargetView = tView

        val tParams = WindowManager.LayoutParams(
            touchWidth,
            touchHeight,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (initialCx - touchWidth / 2f).toInt()
            y = (initialCy - 6f * density).toInt()
            alpha = 1.0f
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        touchLayoutParams = tParams

        try {
            windowManager.addView(tView, tParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateTouchTargetPosition(cx: Float, cy: Float) {
        val tView = touchTargetView ?: return
        val tParams = touchLayoutParams ?: return
        val touchWidth = (prefs.charmSizeDp * density + 16f * density).toInt()
        val touchHeight = (prefs.charmSizeDp * density + 24f * density).toInt()

        val targetX = (cx - touchWidth / 2f).toInt()
        val targetY = (cy - 6f * density).toInt()

        if (abs(targetX - lastTouchTargetX) >= 2 || abs(targetY - lastTouchTargetY) >= 2 ||
            tParams.width != touchWidth || tParams.height != touchHeight) {
            lastTouchTargetX = targetX
            lastTouchTargetY = targetY
            tParams.x = targetX
            tParams.y = targetY
            tParams.width = touchWidth
            tParams.height = touchHeight
            try {
                windowManager.updateViewLayout(tView, tParams)
            } catch (_: Exception) {}
        }
    }

    private fun applyPreferences() {
        val view = renderView ?: return

        view.ropeLengthPx = prefs.ropeLengthDp * density
        view.ropeThicknessPx = prefs.ropeThicknessDp * density
        view.ropeStyle = prefs.ropeStyle
        view.connectorStyle = prefs.connectorStyle
        view.ropeColor = prefs.ropeColor
        view.charmSizePx = prefs.charmSizeDp * density
        view.horizontalPercent = prefs.horizontalPercent
        view.gravityMultiplier = prefs.gravityPercent / 100f
        view.setCharm(prefs.selectedCharm, prefs.customImagePath)

        updateTouchTargetPosition(view.getCharmX(), view.getCharmY())
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            HanglyPreferences.KEY_ENABLED -> {
                if (!prefs.isEnabled) {
                    stopSelf()
                }
            }
            HanglyPreferences.KEY_TILT_PHYSICS -> {
                if (prefs.isTiltPhysicsEnabled && isScreenOn) {
                    registerSensorListener()
                } else {
                    unregisterSensorListener()
                    renderView?.applyTiltForce(0f)
                }
            }
            else -> {
                applyPreferences()
            }
        }
    }

    private fun registerSensorListener() {
        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    private fun unregisterSensorListener() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !prefs.isTiltPhysicsEnabled || !isScreenOn) return

        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val ax = event.values[0]
            if (abs(ax - lastReportedAx) > 0.25f) {
                lastReportedAx = ax
                val tiltMultiplier = -180f * density
                renderView?.applyTiltForce(ax * tiltMultiplier)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        renderView?.post {
            val view = renderView ?: return@post
            val params = renderLayoutParams ?: return@post
            val displayHeight = resources.displayMetrics.heightPixels
            params.height = (displayHeight * 0.75f).toInt()
            try {
                windowManager.updateViewLayout(view, params)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            view.horizontalPercent = prefs.horizontalPercent
            updateTouchTargetPosition(view.getCharmX(), view.getCharmY())
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(screenStateReceiver)
        } catch (_: Exception) {}

        prefs.unregisterListener(this)
        unregisterSensorListener()

        touchTargetView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            touchTargetView = null
        }

        renderView?.let {
            it.stopAnimationLoop()
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            renderView = null
        }
    }
}
