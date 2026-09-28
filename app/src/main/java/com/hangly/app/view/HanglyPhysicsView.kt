package com.hangly.app.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.util.AttributeSet
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.hangly.app.R
import com.hangly.app.data.HanglyPreferences
import com.hangly.app.physics.HanglyPhysics
import com.hangly.app.utils.StatusBarHelper
import java.io.File
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * High-performance, battery-friendly Hangly View.
 * Renders realistic Hangly Mac Spider Web, Gold Chain, Classic, Beads, and Ribbon.
 */
class HanglyPhysicsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), Choreographer.FrameCallback {

    private val density = context.resources.displayMetrics.density

    var ropeLengthPx: Float = 130f * density
        set(value) {
            field = value
            physics.ropeLengthPx = value
            wakeAndAnimate()
        }

    var ropeThicknessPx: Float = 4f * density
        set(value) {
            field = value
            updateRopeDimensions()
            invalidate()
        }

    var ropeStyle: String = HanglyPreferences.ROPE_STYLE_CLASSIC
        set(value) {
            field = value
            invalidate()
        }

    var connectorStyle: String = HanglyPreferences.CONNECTOR_CRESCENT
        set(value) {
            field = value
            invalidate()
        }

    var ropeColor: Int = Color.parseColor("#212121")
        set(value) {
            field = value
            updateRopeColors()
            invalidate()
        }

    var charmSizePx: Float = 68f * density
        set(value) {
            field = value
            reloadCharmBitmap()
            invalidate()
        }

    var horizontalPercent: Float = 0.5f
        set(value) {
            field = value.coerceIn(0.05f, 0.95f)
            updateAnchor()
            invalidate()
        }

    var gravityMultiplier: Float = 0.75f
        set(value) {
            field = value
            physics.gravityMultiplier = value
            wakeAndAnimate()
        }

    var isInteractive: Boolean = true
    var allowAnchorDragging: Boolean = true

    var onPullTriggered: ((isLeftSide: Boolean) -> Unit)? = null
    var onAnchorPositionChanged: ((newPercent: Float) -> Unit)? = null
    var onDragStateChanged: ((isDragging: Boolean) -> Unit)? = null
    var onRestingStateChanged: ((isResting: Boolean) -> Unit)? = null
    var onCharmPositionChanged: ((cx: Float, cy: Float) -> Unit)? = null

    val isBeingDragged: Boolean get() = physics.isBeingDragged || isDraggingAnchor

    fun getCharmX(): Float = physics.getCharmX()
    fun getCharmY(): Float = physics.getCharmY()

    fun handleCharmTouchDown(screenX: Float, screenY: Float) {
        physics.isBeingDragged = true
        physics.touchX = screenX
        physics.touchY = screenY
        hasTriggeredPullHaptic = false
        lastTouchX = screenX
        lastTouchY = screenY
        lastTouchTime = SystemClock.uptimeMillis()
        onDragStateChanged?.invoke(true)
        wakeAndAnimate()
    }

    fun handleCharmTouchMove(screenX: Float, screenY: Float) {
        if (!physics.isBeingDragged) return
        val now = SystemClock.uptimeMillis()
        physics.touchX = screenX
        physics.touchY = screenY

        val pullDist = physics.getPullDownDistance()
        if (pullDist >= pullThresholdPx && !hasTriggeredPullHaptic) {
            hasTriggeredPullHaptic = true
            StatusBarHelper.vibrate(context, 40)
        } else if (pullDist < pullThresholdPx) {
            hasTriggeredPullHaptic = false
        }

        lastTouchX = screenX
        lastTouchY = screenY
        lastTouchTime = now
        wakeAndAnimate()
    }

    fun handleCharmTouchUp(screenX: Float, screenY: Float) {
        if (!physics.isBeingDragged) return
        val now = SystemClock.uptimeMillis()
        val pullDist = physics.getPullDownDistance()
        val isLeftSide = physics.anchorX < width / 2f

        if (pullDist >= pullThresholdPx) {
            onPullTriggered?.invoke(isLeftSide)
        }

        val dt = max(1L, now - lastTouchTime)
        val vx = (screenX - lastTouchX) / dt * 15f
        val vy = (screenY - lastTouchY) / dt * 15f
        physics.applyImpulse(vx, vy)

        physics.isBeingDragged = false
        hasTriggeredPullHaptic = false
        onDragStateChanged?.invoke(false)
        wakeAndAnimate()
    }

    private val physics = HanglyPhysics(density, ropeLengthPx, gravityMultiplier)

    // Paints
    private val ropePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = ropeThicknessPx
        color = ropeColor
    }

    // Realistic Hangly Mac Spider Web Paints
    private val webCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 2.4f * density
        color = Color.parseColor("#FFFFFF")
    }

    private val webSheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 4.2f * density
        color = Color.parseColor("#40FFFFFF") // Soft glistening aura
    }

    private val webTwistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 1.2f * density
        color = Color.parseColor("#D0E8FF") // Spun silk spiral
    }

    private val webFanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 1.2f * density
        color = Color.parseColor("#B0FFFFFF")
    }

    private val chainOuterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = ropeColor
    }

    private val chainInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#424242")
    }

    private val beadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ropeColor
    }

    private val beadShinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    // Braided Rope Paints
    private val braidStrandPaint1 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = ropeColor
    }

    private val braidStrandPaint2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = lightenColor(ropeColor, 0.22f)
    }

    private val braidStrandPaint3 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = darkenColor(ropeColor, 0.72f)
    }

    private val anchorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#37474F")
    }

    private val cuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#E0181B28")
    }

    private val cueTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        textSize = 12f * density
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val ropePath = Path()
    private val webSpiralPath = Path()
    private val braidPath1 = Path()
    private val braidPath2 = Path()
    private val braidPath3 = Path()
    private val cueRect = RectF()

    // Connector Clasp Paints (Small graphic ring / crescent moon shape)
    private val connectorBasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#CFD8DC")
    }

    private val connectorShinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 1.4f * density
        color = Color.WHITE
    }

    private val connectorShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 1.1f * density
        color = Color.parseColor("#607D8B")
    }

    private val connectorRivetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#78909C")
    }

    private val connectorPath = Path()
    private val connectorCrescentPath = Path()

    private var charmBitmap: Bitmap? = null
    private var currentCharmType: String = HanglyPreferences.CHARM_SPIDERMAN
    private var customImagePath: String? = null

    private var isAnimating = false
    private var lastFrameTimeNanos: Long = 0L
    private var isDraggingAnchor = false
    private var hasTriggeredPullHaptic = false
    private val pullThresholdPx = 80f * density

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var lastTouchTime = 0L

    init {
        updateAnchor()
        updateRopeDimensions()
        updateRopeColors()
        reloadCharmBitmap()
    }

    private fun updateRopeDimensions() {
        ropePaint.strokeWidth = ropeThicknessPx

        // Web silk strands scale with rope thickness
        webCorePaint.strokeWidth = (ropeThicknessPx * 0.65f).coerceAtLeast(1.5f * density)
        webSheenPaint.strokeWidth = (ropeThicknessPx * 1.35f).coerceAtLeast(3f * density)
        webTwistPaint.strokeWidth = (ropeThicknessPx * 0.38f).coerceAtLeast(1f * density)
        webFanPaint.strokeWidth = (ropeThicknessPx * 0.38f).coerceAtLeast(1f * density)

        // Chain links scale with rope thickness
        chainOuterPaint.strokeWidth = (ropeThicknessPx * 0.45f).coerceAtLeast(1.5f * density)
        chainInnerPaint.strokeWidth = (ropeThicknessPx * 0.28f).coerceAtLeast(1f * density)

        // Braided rope strands scale with rope thickness
        braidStrandPaint1.strokeWidth = (ropeThicknessPx * 0.55f).coerceAtLeast(1.5f * density)
        braidStrandPaint2.strokeWidth = (ropeThicknessPx * 0.55f).coerceAtLeast(1.5f * density)
        braidStrandPaint3.strokeWidth = (ropeThicknessPx * 0.55f).coerceAtLeast(1.5f * density)
    }

    private fun updateRopeColors() {
        ropePaint.color = ropeColor

        // Chain uses ropeColor with metallic gradient/depth
        chainOuterPaint.color = ropeColor
        chainInnerPaint.color = darkenColor(ropeColor, 0.72f)

        // Web adapts to ropeColor: default or tinted silk
        val isWhiteOrNearWhite = (Color.red(ropeColor) > 230 && Color.green(ropeColor) > 230 && Color.blue(ropeColor) > 230)
        if (isWhiteOrNearWhite) {
            webCorePaint.color = Color.WHITE
            webSheenPaint.color = Color.parseColor("#45FFFFFF")
            webTwistPaint.color = Color.parseColor("#D0E8FF")
            webFanPaint.color = Color.parseColor("#B0FFFFFF")
        } else {
            webCorePaint.color = ropeColor
            val red = Color.red(ropeColor)
            val green = Color.green(ropeColor)
            val blue = Color.blue(ropeColor)
            webSheenPaint.color = Color.argb(0x44, red, green, blue)
            webTwistPaint.color = lightenColor(ropeColor, 0.35f)
            webFanPaint.color = Color.argb(0x88, red, green, blue)
        }

        // Beads use ropeColor with gloss shine
        beadPaint.color = ropeColor
        beadShinePaint.color = Color.WHITE

        // Braided strands use ropeColor with 3D weave shading
        braidStrandPaint1.color = ropeColor
        braidStrandPaint2.color = lightenColor(ropeColor, 0.22f)
        braidStrandPaint3.color = darkenColor(ropeColor, 0.75f)
    }

    private fun darkenColor(color: Int, factor: Float): Int {
        val a = Color.alpha(color)
        val r = (Color.red(color) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return Color.argb(a, r, g, b)
    }

    private fun lightenColor(color: Int, factor: Float): Int {
        val a = Color.alpha(color)
        val r = (Color.red(color) + (255 - Color.red(color)) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) + (255 - Color.green(color)) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) + (255 - Color.blue(color)) * factor).toInt().coerceIn(0, 255)
        return Color.argb(a, r, g, b)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateAnchor()
        if (oldw == 0 && oldh == 0) {
            physics.resetPositions(physics.anchorX, physics.anchorY)
        }
        wakeAndAnimate()
    }

    private fun updateAnchor() {
        val anchorX = width * horizontalPercent
        val anchorY = 0f
        physics.setAnchor(anchorX, anchorY)
        onCharmPositionChanged?.invoke(physics.getCharmX(), physics.getCharmY())
    }

    fun setCharm(charmType: String, customPath: String? = null) {
        currentCharmType = charmType
        customImagePath = customPath
        reloadCharmBitmap()
        invalidate()
    }

    fun applyTiltForce(forceX: Float) {
        physics.tiltForceX = forceX
        if (!physics.isResting) {
            wakeAndAnimate()
        }
    }

    private fun reloadCharmBitmap() {
        val size = charmSizePx.toInt().coerceAtLeast(24)
        charmBitmap?.recycle()
        charmBitmap = null

        try {
            if (currentCharmType == HanglyPreferences.CHARM_CUSTOM && customImagePath != null) {
                val file = File(customImagePath!!)
                if (file.exists()) {
                    val decoded = BitmapFactory.decodeFile(file.absolutePath)
                    if (decoded != null) {
                        charmBitmap = Bitmap.createScaledBitmap(decoded, size, size, true)
                        return
                    }
                }
            }

            val drawableRes = when (currentCharmType) {
                HanglyPreferences.CHARM_EVIL_EYE -> R.drawable.ic_evil_eye
                HanglyPreferences.CHARM_CAP_SHIELD -> R.drawable.ic_cap_shield
                HanglyPreferences.CHARM_LUCKY_CAT -> R.drawable.ic_lucky_cat
                HanglyPreferences.CHARM_GHOST -> R.drawable.ic_ghost
                else -> R.drawable.ic_spiderman
            }

            val drawable: Drawable? = ContextCompat.getDrawable(context, drawableRes)
            if (drawable != null) {
                charmBitmap = drawable.toBitmap(size, size, Bitmap.Config.ARGB_8888)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun wakeAndAnimate() {
        physics.wakeUp()
        onRestingStateChanged?.invoke(false)
        if (!isAnimating) {
            isAnimating = true
            lastFrameTimeNanos = 0L
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun stopAnimationLoop() {
        isAnimating = false
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!isAnimating) return

        if (lastFrameTimeNanos == 0L) {
            lastFrameTimeNanos = frameTimeNanos
        }
        val dt = ((frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f).coerceIn(0.001f, 0.033f)
        lastFrameTimeNanos = frameTimeNanos

        physics.update(dt)
        invalidate()

        onCharmPositionChanged?.invoke(physics.getCharmX(), physics.getCharmY())

        if (physics.isResting && !physics.isBeingDragged) {
            isAnimating = false
            requestLayout()
            onRestingStateChanged?.invoke(true)
        } else {
            requestLayout()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val anchorX = physics.anchorX
        val anchorY = physics.anchorY

        // 1. Top clamp / anchor
        if (ropeStyle != HanglyPreferences.ROPE_STYLE_WEB) {
            val clampWidth = 24f * density
            val clampHeight = 6f * density
            canvas.drawRoundRect(
                anchorX - clampWidth / 2,
                anchorY,
                anchorX + clampWidth / 2,
                anchorY + clampHeight,
                clampHeight / 2,
                clampHeight / 2,
                anchorPaint
            )
        }

        // 2. Draw rope
        drawRopeStyle(canvas)

        // 3. Draw Hanging Charm & Connector
        val charmX = physics.getCharmX()
        val charmY = physics.getCharmY()
        val rotationDeg = physics.getCharmRotationDegrees()

        canvas.save()
        canvas.translate(charmX, charmY)
        canvas.rotate(rotationDeg)

        // Draw Charm Image
        charmBitmap?.let { bmp ->
            val halfW = bmp.width / 2f
            canvas.drawBitmap(bmp, -halfW, 0f, null)
        }

        // Draw Connector Clasp (Small graphic ring / crescent moon connecting rope and image)
        drawCharmConnector(canvas)

        canvas.restore()

        // 4. Draw Pull-down cue badge
        val pullDist = physics.getPullDownDistance()
        if (physics.isBeingDragged && pullDist > 30f * density) {
            val isLeftSide = anchorX < width / 2f
            val cueText = if (pullDist >= pullThresholdPx) {
                if (isLeftSide) "Release: Inbuilt Notifications 🔔" else "Release: Inbuilt Quick Settings ⚡"
            } else {
                if (isLeftSide) "Pull for Notifications" else "Pull for Quick Settings"
            }

            val badgeWidth = cueTextPaint.measureText(cueText) + 28f * density
            val badgeHeight = 28f * density
            val badgeX = charmX
            val badgeY = charmY + charmSizePx + 24f * density

            cueRect.set(
                badgeX - badgeWidth / 2,
                badgeY - badgeHeight / 2,
                badgeX + badgeWidth / 2,
                badgeY + badgeHeight / 2
            )
            canvas.drawRoundRect(cueRect, 14f * density, 14f * density, cuePaint)
            canvas.drawText(cueText, badgeX, badgeY + 4f * density, cueTextPaint)
        }
    }

    /**
     * Renders a small metallic graphic connector (Crescent Moon or Jump Ring)
     * physically linking the hanging rope strand to the top-center of the charm image.
     * Guaranteed to perfectly attach regardless of charm image dimensions or scale.
     */
    private fun drawCharmConnector(canvas: Canvas) {
        val ringRadius = (ropeThicknessPx * 1.35f).coerceIn(6f * density, 9.5f * density)
        val connectorH = ringRadius * 2.2f

        if (connectorStyle == HanglyPreferences.CONNECTOR_RING) {
            // --- 1. JUMP RING CONNECTOR ---
            // Top eyelet connecting to rope end
            val topLoopY = -connectorH * 0.48f
            canvas.drawCircle(0f, topLoopY, 2.6f * density, connectorBasePaint)
            canvas.drawCircle(0f, topLoopY, 1.2f * density, connectorShinePaint)

            // Main circular jump ring body
            val cy = -ringRadius * 0.35f
            val rOuter = ringRadius
            val rInner = ringRadius * 0.58f

            connectorPath.reset()
            connectorPath.addCircle(0f, cy, rOuter, Path.Direction.CW)
            connectorPath.addCircle(0f, cy, rInner, Path.Direction.CCW)
            canvas.drawPath(connectorPath, connectorBasePaint)

            // 3D metallic bevel edges
            connectorShadowPaint.strokeWidth = 1f * density
            canvas.drawCircle(0f, cy, rInner, connectorShadowPaint)
            canvas.drawCircle(0f, cy, rOuter, connectorShadowPaint)

            // Highlight glint on upper-left curve
            val gleamRect = RectF(-rOuter, cy - rOuter, rOuter, cy + rOuter)
            connectorShinePaint.strokeWidth = 1.6f * density
            canvas.drawArc(gleamRect, 200f, 85f, false, connectorShinePaint)

            // Bottom metallic clasp bracket securely gripping charm top edge
            val claspW = ringRadius * 1.15f
            val claspH = 4.2f * density
            canvas.drawRoundRect(-claspW / 2, -1f * density, claspW / 2, claspH - 1f * density, 2f * density, 2f * density, connectorShadowPaint)
            canvas.drawRoundRect(-claspW / 2 + 0.8f * density, 0f, claspW / 2 - 0.8f * density, claspH - 1.8f * density, 1.5f * density, 1.5f * density, connectorBasePaint)
            canvas.drawCircle(0f, 1.2f * density, 1.5f * density, connectorRivetPaint)
            canvas.drawCircle(0f, 1.0f * density, 0.7f * density, connectorShinePaint)

        } else {
            // --- 2. CRESCENT MOON CONNECTOR (Default) ---
            val cy = -ringRadius * 0.35f
            val moonR = ringRadius * 1.15f

            // Top eyelet connecting directly to rope end
            val eyeletY = -connectorH * 0.52f
            canvas.drawCircle(0f, eyeletY, 2.8f * density, connectorShadowPaint)
            canvas.drawCircle(0f, eyeletY, 2.2f * density, connectorBasePaint)
            canvas.drawCircle(0f, eyeletY, 1.0f * density, connectorShinePaint)

            // Crescent Moon Body
            connectorCrescentPath.reset()
            val outerRect = RectF(-moonR, cy - moonR, moonR, cy + moonR)
            connectorCrescentPath.arcTo(outerRect, 110f, 240f, true)
            val innerRect = RectF(-moonR * 0.72f + 2f * density, cy - moonR * 0.82f, moonR * 0.88f + 2f * density, cy + moonR * 0.82f)
            connectorCrescentPath.arcTo(innerRect, 350f, -200f, false)
            connectorCrescentPath.close()

            // Fill metallic crescent
            canvas.drawPath(connectorCrescentPath, connectorBasePaint)

            // 3D Shadow edge
            connectorShadowPaint.strokeWidth = 1.1f * density
            canvas.drawPath(connectorCrescentPath, connectorShadowPaint)

            // Specular shine along outer crescent curve
            connectorShinePaint.strokeWidth = 1.8f * density
            val shineRect = RectF(-moonR + 0.8f * density, cy - moonR + 0.8f * density, moonR - 0.8f * density, cy + moonR - 0.8f * density)
            canvas.drawArc(shineRect, 140f, 110f, false, connectorShinePaint)

            // Small star / gleam accent on crest
            canvas.drawCircle(-moonR * 0.52f, cy, 1.2f * density, connectorShinePaint)

            // Bottom metallic clasp bracket securely gripping charm top edge
            val claspW = ringRadius * 1.1f
            val claspH = 4.2f * density
            canvas.drawRoundRect(-claspW / 2, -1f * density, claspW / 2, claspH - 1f * density, 2f * density, 2f * density, connectorShadowPaint)
            canvas.drawRoundRect(-claspW / 2 + 0.8f * density, 0f, claspW / 2 - 0.8f * density, claspH - 1.8f * density, 1.5f * density, 1.5f * density, connectorBasePaint)
            canvas.drawCircle(0f, 1.2f * density, 1.5f * density, connectorRivetPaint)
            canvas.drawCircle(0f, 1.0f * density, 0.7f * density, connectorShinePaint)
        }
    }

    private fun drawRopeStyle(canvas: Canvas) {
        val nodes = physics.nodes

        when (ropeStyle) {
            HanglyPreferences.ROPE_STYLE_WEB -> {
                drawRealisticHanglyMacWeb(canvas, nodes)
            }
            HanglyPreferences.ROPE_STYLE_CHAIN -> {
                drawChainRope(canvas, nodes)
            }
            HanglyPreferences.ROPE_STYLE_BEADS -> {
                drawBeadedRope(canvas, nodes)
            }
            HanglyPreferences.ROPE_STYLE_BRAIDED,
            HanglyPreferences.ROPE_STYLE_RIBBON -> {
                drawBraidedRope(canvas, nodes)
            }
            else -> { // ROPE_STYLE_CLASSIC
                drawClassicRope(canvas, nodes)
            }
        }
    }

    /**
     * Authentic Hangly Mac Spider-Man Web:
     * - Screen notch silk anchor fan
     * - Pure glistening silk strand (adapts to rope thickness and color)
     * - Double-twisted spiral silk fibers
     * - Bottom web wrap knot
     */
    private fun drawRealisticHanglyMacWeb(canvas: Canvas, nodes: Array<HanglyPhysics.Node>) {
        val ax = nodes[0].x
        val ay = nodes[0].y

        // 1. Top Web Anchor Fan adhering to notch (scales with thickness)
        val scaleRatio = (ropeThicknessPx / (4f * density)).coerceIn(0.5f, 3.5f)
        val fanSpread = 16f * density * scaleRatio
        canvas.drawLine(ax, ay, ax - fanSpread, ay + 6f * density * scaleRatio, webFanPaint)
        canvas.drawLine(ax, ay, ax - fanSpread * 0.5f, ay + 12f * density * scaleRatio, webFanPaint)
        canvas.drawLine(ax, ay, ax + fanSpread * 0.5f, ay + 12f * density * scaleRatio, webFanPaint)
        canvas.drawLine(ax, ay, ax + fanSpread, ay + 6f * density * scaleRatio, webFanPaint)
        // Silk web pad
        canvas.drawCircle(ax, ay + 2f * density * scaleRatio, (ropeThicknessPx * 0.9f).coerceAtLeast(3f * density), webCorePaint)

        // 2. Build Smooth Path along nodes
        ropePath.reset()
        ropePath.moveTo(nodes[0].x, nodes[0].y)
        for (i in 1 until HanglyPhysics.NUM_NODES) {
            val p0 = nodes[i - 1]
            val p1 = nodes[i]
            val midX = (p0.x + p1.x) / 2
            val midY = (p0.y + p1.y) / 2
            ropePath.quadTo(p0.x, p0.y, midX, midY)
        }
        val lastNode = nodes[HanglyPhysics.NUM_NODES - 1]
        ropePath.lineTo(lastNode.x, lastNode.y)

        // 3. Draw Soft Glistening Sheen Aura
        canvas.drawPath(ropePath, webSheenPaint)

        // 4. Draw Core Silk Strand
        canvas.drawPath(ropePath, webCorePaint)

        // 5. Draw Fine Twisted Silk Spiral Fibers along the strand
        webSpiralPath.reset()
        var isFirst = true
        val steps = 28
        for (step in 0..steps) {
            val t = step.toFloat() / steps
            val nodeIdx = (t * (HanglyPhysics.NUM_NODES - 1)).toInt().coerceIn(0, HanglyPhysics.NUM_NODES - 2)
            val localT = (t * (HanglyPhysics.NUM_NODES - 1)) - nodeIdx

            val nA = nodes[nodeIdx]
            val nB = nodes[nodeIdx + 1]

            val baseX = nA.x + (nB.x - nA.x) * localT
            val baseY = nA.y + (nB.y - nA.y) * localT

            val angle = step * 1.8f
            val waveOffset = sin(angle) * (ropeThicknessPx * 0.45f).coerceAtLeast(1.5f * density)

            val dx = nB.x - nA.x
            val dy = nB.y - nA.y
            val len = hypot(dx, dy).coerceAtLeast(1f)
            val perpX = -dy / len
            val perpY = dx / len

            val px = baseX + perpX * waveOffset
            val py = baseY + perpY * waveOffset

            if (isFirst) {
                webSpiralPath.moveTo(px, py)
                isFirst = false
            } else {
                webSpiralPath.lineTo(px, py)
            }
        }
        canvas.drawPath(webSpiralPath, webTwistPaint)

        // 6. Bottom Web Knot wrapping boots/charm
        val knotRadius = (ropeThicknessPx * 0.9f).coerceAtLeast(3.5f * density)
        canvas.drawCircle(lastNode.x, lastNode.y, knotRadius, webCorePaint)
        canvas.drawCircle(lastNode.x, lastNode.y, knotRadius * 0.6f, beadShinePaint)
    }

    private fun drawClassicRope(canvas: Canvas, nodes: Array<HanglyPhysics.Node>) {
        ropePath.reset()
        ropePath.moveTo(nodes[0].x, nodes[0].y)
        for (i in 1 until HanglyPhysics.NUM_NODES) {
            val p0 = nodes[i - 1]
            val p1 = nodes[i]
            val midX = (p0.x + p1.x) / 2
            val midY = (p0.y + p1.y) / 2
            ropePath.quadTo(p0.x, p0.y, midX, midY)
        }
        val lastNode = nodes[HanglyPhysics.NUM_NODES - 1]
        ropePath.lineTo(lastNode.x, lastNode.y)
        canvas.drawPath(ropePath, ropePaint)
    }

    private fun drawChainRope(canvas: Canvas, nodes: Array<HanglyPhysics.Node>) {
        // Link sizes scale dynamically with rope thickness
        val linkWidth = (ropeThicknessPx * 1.7f).coerceAtLeast(5f * density)
        val linkLength = linkWidth * 1.85f

        for (i in 0 until HanglyPhysics.NUM_NODES - 1) {
            val n1 = nodes[i]
            val n2 = nodes[i + 1]
            val dx = n2.x - n1.x
            val dy = n2.y - n1.y
            val segDist = hypot(dx, dy)
            val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()

            val numLinks = max(1, (segDist / (linkLength * 0.72f)).toInt())
            for (j in 0 until numLinks) {
                val t = (j + 0.5f) / numLinks
                val lx = n1.x + dx * t
                val ly = n1.y + dy * t

                canvas.save()
                canvas.translate(lx, ly)
                canvas.rotate(angle)

                if (j % 2 == 0) {
                    val rect = RectF(-linkLength / 2, -linkWidth / 2, linkLength / 2, linkWidth / 2)
                    canvas.drawRoundRect(rect, linkWidth / 2, linkWidth / 2, chainOuterPaint)
                } else {
                    val rect = RectF(-linkLength / 2, -linkWidth / 3.8f, linkLength / 2, linkWidth / 3.8f)
                    canvas.drawRoundRect(rect, linkWidth / 3.8f, linkWidth / 3.8f, chainInnerPaint)
                }
                canvas.restore()
            }
        }
    }

    private fun drawBeadedRope(canvas: Canvas, nodes: Array<HanglyPhysics.Node>) {
        drawClassicRope(canvas, nodes)

        val beadRadius = (ropeThicknessPx * 1.25f).coerceAtLeast(3.5f * density)
        for (i in 0 until HanglyPhysics.NUM_NODES - 1) {
            val n1 = nodes[i]
            val n2 = nodes[i + 1]
            val dx = n2.x - n1.x
            val dy = n2.y - n1.y
            val segDist = hypot(dx, dy)
            val numBeads = max(2, (segDist / (beadRadius * 2.3f)).toInt())

            for (j in 0 until numBeads) {
                val t = (j + 0.5f) / numBeads
                val bx = n1.x + dx * t
                val by = n1.y + dy * t

                canvas.drawCircle(bx, by, beadRadius, beadPaint)
                canvas.drawCircle(bx - beadRadius * 0.35f, by - beadRadius * 0.35f, beadRadius * 0.35f, beadShinePaint)
            }
        }
    }

    private fun drawBraidedRope(canvas: Canvas, nodes: Array<HanglyPhysics.Node>) {
        val braidWidth = (ropeThicknessPx * 1.4f).coerceAtLeast(4f * density)
        val steps = 36

        braidPath1.reset()
        braidPath2.reset()
        braidPath3.reset()

        var isFirst = true

        for (step in 0..steps) {
            val t = step.toFloat() / steps
            val nodeIdx = (t * (HanglyPhysics.NUM_NODES - 1)).toInt().coerceIn(0, HanglyPhysics.NUM_NODES - 2)
            val localT = (t * (HanglyPhysics.NUM_NODES - 1)) - nodeIdx

            val nA = nodes[nodeIdx]
            val nB = nodes[nodeIdx + 1]

            val baseX = nA.x + (nB.x - nA.x) * localT
            val baseY = nA.y + (nB.y - nA.y) * localT

            val dx = nB.x - nA.x
            val dy = nB.y - nA.y
            val len = hypot(dx, dy).coerceAtLeast(1f)
            val perpX = -dy / len
            val perpY = dx / len

            val angle = step * 1.6f
            val off1 = sin(angle) * (braidWidth * 0.42f)
            val off2 = sin(angle + 2.094f) * (braidWidth * 0.42f)
            val off3 = sin(angle + 4.188f) * (braidWidth * 0.42f)

            val p1x = baseX + perpX * off1
            val p1y = baseY + perpY * off1
            val p2x = baseX + perpX * off2
            val p2y = baseY + perpY * off2
            val p3x = baseX + perpX * off3
            val p3y = baseY + perpY * off3

            if (isFirst) {
                braidPath1.moveTo(p1x, p1y)
                braidPath2.moveTo(p2x, p2y)
                braidPath3.moveTo(p3x, p3y)
                isFirst = false
            } else {
                braidPath1.lineTo(p1x, p1y)
                braidPath2.lineTo(p2x, p2y)
                braidPath3.lineTo(p3x, p3y)
            }
        }

        // Draw 3 interwoven braid strands
        canvas.drawPath(braidPath3, braidStrandPaint3)
        canvas.drawPath(braidPath1, braidStrandPaint1)
        canvas.drawPath(braidPath2, braidStrandPaint2)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isInteractive) return false

        val x = event.x
        val y = event.y
        val now = SystemClock.uptimeMillis()

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val charmX = physics.getCharmX()
                val charmY = physics.getCharmY()
                val touchDistToCharm = hypot(x - charmX, y - (charmY + charmSizePx / 2))

                val isNearAnchor = allowAnchorDragging && y <= 40f * density && abs(x - physics.anchorX) < 40f * density
                val isNearCharm = touchDistToCharm <= charmSizePx * 1.3f ||
                        (y <= charmY + charmSizePx && abs(x - charmX) < 30f * density)

                if (!isNearAnchor && !isNearCharm) {
                    return false
                }

                if (isNearAnchor) {
                    isDraggingAnchor = true
                    onDragStateChanged?.invoke(true)
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }

                if (isNearCharm) {
                    physics.isBeingDragged = true
                    physics.touchX = x
                    physics.touchY = y
                    hasTriggeredPullHaptic = false
                    lastTouchX = x
                    lastTouchY = y
                    lastTouchTime = now
                    onDragStateChanged?.invoke(true)
                    wakeAndAnimate()
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (isDraggingAnchor) {
                    val newPercent = (x / width).coerceIn(0.05f, 0.95f)
                    horizontalPercent = newPercent
                    onAnchorPositionChanged?.invoke(newPercent)
                    return true
                }

                if (physics.isBeingDragged) {
                    physics.touchX = x
                    physics.touchY = y

                    val pullDist = physics.getPullDownDistance()
                    if (pullDist >= pullThresholdPx && !hasTriggeredPullHaptic) {
                        hasTriggeredPullHaptic = true
                        StatusBarHelper.vibrate(context, 40)
                    } else if (pullDist < pullThresholdPx) {
                        hasTriggeredPullHaptic = false
                    }

                    lastTouchX = x
                    lastTouchY = y
                    lastTouchTime = now
                    wakeAndAnimate()
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                onDragStateChanged?.invoke(false)
                if (isDraggingAnchor) {
                    isDraggingAnchor = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }

                if (physics.isBeingDragged) {
                    val pullDist = physics.getPullDownDistance()
                    val isLeftSide = physics.anchorX < width / 2f

                    if (pullDist >= pullThresholdPx) {
                        onPullTriggered?.invoke(isLeftSide)
                    }

                    val dt = max(1L, now - lastTouchTime)
                    val vx = (x - lastTouchX) / dt * 15f
                    val vy = (y - lastTouchY) / dt * 15f
                    physics.applyImpulse(vx, vy)

                    physics.isBeingDragged = false
                    hasTriggeredPullHaptic = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    wakeAndAnimate()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    fun getTouchRegions(): List<Rect> {
        val list = ArrayList<Rect>()
        if (!isInteractive) return list

        if (physics.isBeingDragged || isDraggingAnchor) {
            // While actively dragging, capture the whole overlay bounds for smooth gesture tracking
            list.add(Rect(0, 0, width, height))
            return list
        }

        val density = context.resources.displayMetrics.density

        // 1. Anchor notch target (only if user allowed anchor dragging)
        if (allowAnchorDragging) {
            val ax = physics.anchorX
            val ay = physics.anchorY
            val ar = 24f * density
            list.add(
                Rect(
                    (ax - ar).toInt().coerceAtLeast(0),
                    0,
                    (ax + ar).toInt().coerceAtMost(width),
                    (ay + 32f * density).toInt()
                )
            )
        }

        // 2. Segmented cord strips strictly hugging the swinging rope nodes
        // Each node segment has a slender touch box following the swinging cord
        val cordHalfWidth = (ropeThicknessPx / 2f + 8f * density)
        val nodes = physics.nodes
        for (i in 0 until HanglyPhysics.NUM_NODES - 1) {
            val n1 = nodes[i]
            val n2 = nodes[i + 1]
            val minX = kotlin.math.min(n1.x, n2.x) - cordHalfWidth
            val maxX = kotlin.math.max(n1.x, n2.x) + cordHalfWidth
            val minY = kotlin.math.min(n1.y, n2.y) - 2f * density
            val maxY = kotlin.math.max(n1.y, n2.y) + 2f * density

            list.add(
                Rect(
                    minX.toInt().coerceAtLeast(0),
                    minY.toInt().coerceAtLeast(0),
                    maxX.toInt().coerceAtMost(width),
                    maxY.toInt().coerceAtMost(height)
                )
            )
        }

        // 3. Charm touch bounds (exact circular/square target around the dangling charm)
        val cx = physics.getCharmX()
        val cy = physics.getCharmY() + charmSizePx / 2f
        val cr = charmSizePx / 2f + 4f * density
        list.add(
            Rect(
                (cx - cr).toInt().coerceAtLeast(0),
                (cy - cr).toInt().coerceAtLeast(0),
                (cx + cr).toInt().coerceAtMost(width),
                (cy + cr).toInt().coerceAtMost(height)
            )
        )

        return list
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        wakeAndAnimate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimationLoop()
    }
}
