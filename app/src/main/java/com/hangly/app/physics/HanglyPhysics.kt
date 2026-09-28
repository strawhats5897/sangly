package com.hangly.app.physics

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Ultra-lightweight Verlet physics simulation.
 * Optimized for minimal CPU & zero idle battery consumption:
 * - Deadband filtering on sensor noise
 * - Fast equilibrium sleep detection
 * - Zero allocations during update loop
 */
class HanglyPhysics(
    private val density: Float,
    var ropeLengthPx: Float,
    var gravityMultiplier: Float = 0.75f // 75% of normal gravity
) {
    companion object {
        const val NUM_NODES = 6
        private const val BASE_GRAVITY = 1800f // Base Earth gravity in screen pixels/s^2
        private const val AIR_DAMPING = 0.978f // Rapid natural settling to conserve battery
        private const val CONSTRAINT_ITERATIONS = 6 // Reduced iteration count, excellent fidelity
        private const val RESTING_VELOCITY_THRESHOLD = 0.25f
        private const val SETTLE_FRAMES_REQUIRED = 8
    }

    class Node(var x: Float, var y: Float) {
        var px: Float = x
        var py: Float = y
        var ax: Float = 0f
        var ay: Float = 0f
    }

    val nodes = Array(NUM_NODES) { Node(0f, 0f) }

    var anchorX: Float = 0f
    var anchorY: Float = 0f

    var isBeingDragged: Boolean = false
    var touchX: Float = 0f
    var touchY: Float = 0f

    var tiltForceX: Float = 0f
        set(value) {
            // Deadband noise gate: filter micro-tremors below 8f to prevent waking CPU
            val filtered = if (abs(value) < 8f * density) 0f else value
            if (abs(filtered - field) > 4f * density) {
                field = filtered
                if (field != 0f) {
                    wakeUp()
                }
            }
        }

    var isResting: Boolean = false
    private var settleFrameCounter: Int = 0

    init {
        resetPositions(0f, 0f)
    }

    fun setAnchor(x: Float, y: Float) {
        val dx = x - anchorX
        val dy = y - anchorY
        anchorX = x
        anchorY = y

        nodes[0].x = anchorX
        nodes[0].y = anchorY
        nodes[0].px = anchorX
        nodes[0].py = anchorY

        if (abs(dx) > 0.5f || abs(dy) > 0.5f) {
            for (i in 1 until NUM_NODES) {
                nodes[i].x += dx
                nodes[i].y += dy
                nodes[i].px += dx
                nodes[i].py += dy
            }
            wakeUp()
        }
    }

    fun resetPositions(x: Float, y: Float) {
        anchorX = x
        anchorY = y
        val segmentLength = ropeLengthPx / (NUM_NODES - 1)
        for (i in 0 until NUM_NODES) {
            val nodeY = y + i * segmentLength
            nodes[i].x = x
            nodes[i].y = nodeY
            nodes[i].px = x
            nodes[i].py = nodeY
            nodes[i].ax = 0f
            nodes[i].ay = 0f
        }
        isResting = false
        settleFrameCounter = 0
    }

    fun wakeUp() {
        isResting = false
        settleFrameCounter = 0
    }

    fun applyImpulse(impulseX: Float, impulseY: Float) {
        if (abs(impulseX) > 0.1f || abs(impulseY) > 0.1f) {
            val last = nodes[NUM_NODES - 1]
            last.px -= impulseX
            last.py -= impulseY
            wakeUp()
        }
    }

    fun update(dt: Float) {
        if (isResting && !isBeingDragged) {
            return
        }

        // Clamp delta time to avoid physics explosion
        val safeDt = min(dt, 0.033f)
        val dtSq = safeDt * safeDt

        // 75% of normal gravity
        val effectiveGravity = BASE_GRAVITY * density * gravityMultiplier

        // 1. Verlet Integration
        var totalMotion = 0f
        for (i in 1 until NUM_NODES) {
            val node = nodes[i]

            // Apply gravity (75% Earth)
            node.ay += effectiveGravity

            // Apply device tilt force
            node.ax += tiltForceX

            val vx = (node.x - node.px) * AIR_DAMPING
            val vy = (node.y - node.py) * AIR_DAMPING

            node.px = node.x
            node.py = node.y

            node.x += vx + node.ax * dtSq
            node.y += vy + node.ay * dtSq

            totalMotion += hypot(vx, vy)

            node.ax = 0f
            node.ay = 0f
        }

        // 2. Solve Distance Constraints
        val segmentLength = ropeLengthPx / (NUM_NODES - 1)

        for (iter in 0 until CONSTRAINT_ITERATIONS) {
            // Anchor is always fixed
            nodes[0].x = anchorX
            nodes[0].y = anchorY

            // Couple bottom node to finger if dragged
            if (isBeingDragged) {
                val lastNode = nodes[NUM_NODES - 1]
                lastNode.x += (touchX - lastNode.x) * 0.55f
                lastNode.y += (touchY - lastNode.y) * 0.55f
            }

            // Relax segment lengths
            for (i in 0 until NUM_NODES - 1) {
                val n1 = nodes[i]
                val n2 = nodes[i + 1]

                var dx = n2.x - n1.x
                var dy = n2.y - n1.y
                val dist = sqrt(dx * dx + dy * dy)
                if (dist == 0f) continue

                val targetDist = if (isBeingDragged && i == NUM_NODES - 2) {
                    segmentLength * 1.15f
                } else {
                    segmentLength
                }

                val diff = (dist - targetDist) / dist
                dx *= diff
                dy *= diff

                if (i == 0) {
                    n2.x -= dx
                    n2.y -= dy
                } else if (isBeingDragged && i + 1 == NUM_NODES - 1) {
                    n1.x += dx * 0.7f
                    n1.y += dy * 0.7f
                    n2.x -= dx * 0.3f
                    n2.y -= dy * 0.3f
                } else {
                    n1.x += dx * 0.5f
                    n1.y += dy * 0.5f
                    n2.x -= dx * 0.5f
                    n2.y -= dy * 0.5f
                }
            }
        }

        // Fast Settle & Sleep Detection to Save 100% CPU & Battery
        if (!isBeingDragged) {
            if (totalMotion < RESTING_VELOCITY_THRESHOLD) {
                settleFrameCounter++
                if (settleFrameCounter >= SETTLE_FRAMES_REQUIRED) {
                    // Lock velocities to zero
                    for (i in 1 until NUM_NODES) {
                        nodes[i].px = nodes[i].x
                        nodes[i].py = nodes[i].y
                    }
                    isResting = true
                }
            } else {
                settleFrameCounter = 0
            }
        }
    }

    /**
     * Charm rotation angle in degrees derived from bottom rope segment
     */
    fun getCharmRotationDegrees(): Float {
        val nPrev = nodes[NUM_NODES - 2]
        val nLast = nodes[NUM_NODES - 1]
        val dx = nLast.x - nPrev.x
        val dy = nLast.y - nPrev.y
        val rad = atan2(dx, dy)
        return Math.toDegrees(-rad.toDouble()).toFloat()
    }

    fun getCharmX(): Float = nodes[NUM_NODES - 1].x
    fun getCharmY(): Float = nodes[NUM_NODES - 1].y

    fun getPullDownDistance(): Float {
        val currentDist = getCharmY() - anchorY
        return max(0f, currentDist - ropeLengthPx)
    }
}
