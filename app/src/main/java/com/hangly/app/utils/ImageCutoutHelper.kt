package com.hangly.app.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/**
 * Helper to process, transform, scale, pan, and convert custom photo charms.
 *
 * Supports arbitrary user-defined pinch-to-zoom and pan regardless of original image size,
 * preserving natural borderless outlines and transparency, and strictly saving as PNG.
 */
object ImageCutoutHelper {

    /**
     * Renders the source bitmap transformed by zoom and pan offsets into a clean, borderless
     * high-resolution square canvas.
     */
    fun createTransformedBitmap(
        source: Bitmap,
        targetSize: Int = 400,
        panOffsetX: Float = 0f,
        panOffsetY: Float = 0f,
        zoomScale: Float = 1.0f
    ): Bitmap {
        val output = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            isDither = true
        }

        val matrix = Matrix()

        // Base scale so largest dimension initially fits targetSize
        val maxDim = max(source.width, source.height).toFloat().coerceAtLeast(1f)
        val baseScale = targetSize.toFloat() / maxDim
        val finalScale = baseScale * zoomScale

        // Center source image, apply scale, then apply pan offsets
        matrix.postTranslate(-source.width / 2f, -source.height / 2f)
        matrix.postScale(finalScale, finalScale)
        matrix.postTranslate(targetSize / 2f + panOffsetX, targetSize / 2f + panOffsetY)

        canvas.drawBitmap(source, matrix, paint)
        return output
    }

    /**
     * Saves any given bitmap strictly as a PNG file, converting JPEGs, JPGs, or WebP
     * to full-color alpha-capable PNG.
     */
    fun saveAsPng(bitmap: Bitmap, destinationFile: File): Boolean {
        return try {
            destinationFile.parentFile?.mkdirs()
            FileOutputStream(destinationFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
