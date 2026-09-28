package com.hangly.app.utils

import android.graphics.Rect
import android.graphics.Region
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * Universal Touch Pass-Through Helper for Android Floating Overlays.
 *
 * Utilizes AOSP WindowManager's OnComputeInternalInsetsListener to restrict touch
 * dispatch strictly to the charm, rope, and anchor pixels.
 *
 * All touches outside these exact coordinates pass straight through to whatever
 * application, browser, keyboard, or system menu is running underneath.
 */
object OverlayTouchHelper {
    private const val TAG = "OverlayTouchHelper"

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                HiddenApiBypass.addHiddenApiExemptions("")
                Log.d(TAG, "HiddenApiBypass initialized successfully")
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to apply HiddenApiBypass: ${e.message}")
            }
        }
    }

    /**
     * Registers OnComputeInternalInsetsListener via reflection.
     * Returns true if successfully hooked, false otherwise.
     */
    fun setupTouchPassthrough(
        view: View,
        touchBoundsProvider: () -> List<Rect>
    ): Boolean {
        return try {
            val listenerClass = Class.forName("android.view.ViewTreeObserver\$OnComputeInternalInsetsListener")
            val addMethod = ViewTreeObserver::class.java.getMethod(
                "addOnComputeInternalInsetsListener",
                listenerClass
            )

            var setTouchableInsetsMethod: Method? = null
            var touchableRegionField: Field? = null

            val proxy = Proxy.newProxyInstance(
                listenerClass.classLoader,
                arrayOf(listenerClass)
            ) { _, method, args ->
                if (method.name == "onComputeInternalInsets" && args != null && args.isNotEmpty()) {
                    val insetsInfo = args[0]
                    try {
                        if (setTouchableInsetsMethod == null) {
                            setTouchableInsetsMethod = insetsInfo.javaClass.getMethod(
                                "setTouchableInsets",
                                Int::class.javaPrimitiveType
                            )
                        }
                        // TOUCHABLE_INSETS_REGION = 3
                        setTouchableInsetsMethod?.invoke(insetsInfo, 3)

                        if (touchableRegionField == null) {
                            touchableRegionField = insetsInfo.javaClass.getField("touchableRegion")
                        }
                        val region = touchableRegionField?.get(insetsInfo) as? Region
                        if (region != null) {
                            region.setEmpty()
                            val rects = touchBoundsProvider()
                            for (rect in rects) {
                                if (!rect.isEmpty) {
                                    region.op(rect, Region.Op.UNION)
                                }
                            }
                        }
                    } catch (e: Throwable) {
                        Log.w(TAG, "Error applying insets: ${e.message}")
                    }
                }
                null
            }

            // If view is already attached, register on current ViewTreeObserver
            if (view.isAttachedToWindow) {
                addMethod.invoke(view.viewTreeObserver, proxy)
                Log.d(TAG, "Registered insets listener on existing ViewTreeObserver")
            }

            // Also register on attach state change to ensure binding to ViewRootImpl's observer
            view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    try {
                        addMethod.invoke(v.viewTreeObserver, proxy)
                        Log.d(TAG, "Registered insets listener on window-attached ViewTreeObserver")
                    } catch (e: Throwable) {
                        Log.w(TAG, "Error adding insets listener on window attach: ${e.message}")
                    }
                }

                override fun onViewDetachedFromWindow(v: View) {}
            })

            Log.d(TAG, "Overlay touch pass-through successfully activated!")
            true
        } catch (e: Throwable) {
            Log.w(TAG, "OnComputeInternalInsetsListener setup failed: ${e.message}")
            false
        }
    }
}
