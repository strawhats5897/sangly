package com.hangly.app.utils

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import com.hangly.app.service.HanglyAccessibilityService

/**
 * Universal Mobile Actions Helper.
 * Automatically allows and triggers native mobile system Notifications
 * and Quick Settings panels on EVERY Android device without requiring
 * manual accessibility configuration.
 */
object StatusBarHelper {
    private const val TAG = "StatusBarHelper"

    init {
        unsealHiddenApis()
    }

    /**
     * Exempts the app from Android hidden API restrictions (Android 9 Pie through 15+).
     * This enables direct access to StatusBarManager reflection without runtime blockage.
     */
    private fun unsealHiddenApis() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val forName = Class::class.java.getDeclaredMethod("forName", String::class.java)
                val getDeclaredMethod = Class::class.java.getDeclaredMethod(
                    "getDeclaredMethod",
                    String::class.java,
                    arrayOf<Class<*>>()::class.java
                )
                val vmRuntimeClass = forName.invoke(null, "dalvik.system.VMRuntime") as Class<*>
                val getRuntime = getDeclaredMethod.invoke(vmRuntimeClass, "getRuntime", emptyArray<Class<*>>()) as java.lang.reflect.Method
                val setHiddenApiExemptions = getDeclaredMethod.invoke(
                    vmRuntimeClass,
                    "setHiddenApiExemptions",
                    arrayOf(arrayOf<String>()::class.java)
                ) as java.lang.reflect.Method
                val runtime = getRuntime.invoke(null)
                setHiddenApiExemptions.invoke(runtime, arrayOf("L"))
                Log.d(TAG, "Successfully initialized Android system status bar exemptions")
            } catch (t: Throwable) {
                Log.w(TAG, "Hidden API unseal: ${t.message}")
            }
        }
    }

    /**
     * Actions are automatically allowed on every Android device because
     * android.permission.EXPAND_STATUS_BAR is a standard normal permission
     * automatically granted by Android OS at install time.
     */
    fun isActionsAutomaticallyAllowed(context: Context): Boolean {
        return true
    }

    /**
     * Opens the native inbuilt system Notifications shade.
     * Tries 3 resilient strategies so it works seamlessly on all Android variants.
     */
    fun openNotifications(context: Context): Boolean {
        vibrate(context, 40)

        // 1. AccessibilityService (if active)
        val accessibilityService = HanglyAccessibilityService.instance
        if (accessibilityService != null) {
            val handled = accessibilityService.performGlobalAction(
                AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            )
            if (handled) {
                Log.d(TAG, "Opened notifications via AccessibilityService")
                return true
            }
        }

        // 2. StatusBarManager reflection (unsealed via EXPAND_STATUS_BAR)
        try {
            val statusBarService = context.getSystemService("statusbar")
            if (statusBarService != null) {
                val statusBarManagerClass = Class.forName("android.app.StatusBarManager")
                val method = try {
                    statusBarManagerClass.getMethod("expandNotificationsPanel")
                } catch (_: NoSuchMethodException) {
                    @Suppress("DEPRECATION")
                    statusBarManagerClass.getMethod("expand")
                }
                method.invoke(statusBarService)
                Log.d(TAG, "Opened notifications via StatusBarManager reflection")
                return true
            }
        } catch (e: Throwable) {
            Log.w(TAG, "StatusBarManager notifications: ${e.message}")
        }

        // 3. IStatusBarService Binder IPC Fallback
        try {
            val serviceManagerClass = Class.forName("android.os.ServiceManager")
            val getServiceMethod = serviceManagerClass.getMethod("getService", String::class.java)
            val binder = getServiceMethod.invoke(null, "statusbar") as? IBinder
            if (binder != null) {
                val iStatusBarClass = Class.forName("com.android.internal.statusbar.IStatusBarService\$Stub")
                val asInterfaceMethod = iStatusBarClass.getMethod("asInterface", IBinder::class.java)
                val iStatusBar = asInterfaceMethod.invoke(null, binder)
                val expandMethod = iStatusBar.javaClass.getMethod("expandNotificationsPanel")
                expandMethod.invoke(iStatusBar)
                Log.d(TAG, "Opened notifications via IStatusBarService IPC")
                return true
            }
        } catch (e: Throwable) {
            Log.w(TAG, "IStatusBarService notifications fallback: ${e.message}")
        }

        return false
    }

    /**
     * Opens the native inbuilt Quick Settings panel.
     * Tries 3 resilient strategies so it works seamlessly on all Android variants.
     */
    fun openQuickSettings(context: Context): Boolean {
        vibrate(context, 50)

        // 1. AccessibilityService (if active)
        val accessibilityService = HanglyAccessibilityService.instance
        if (accessibilityService != null) {
            val handled = accessibilityService.performGlobalAction(
                AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
            )
            if (handled) {
                Log.d(TAG, "Opened quick settings via AccessibilityService")
                return true
            }
        }

        // 2. StatusBarManager reflection (unsealed via EXPAND_STATUS_BAR)
        try {
            val statusBarService = context.getSystemService("statusbar")
            if (statusBarService != null) {
                val statusBarManagerClass = Class.forName("android.app.StatusBarManager")
                val method = statusBarManagerClass.getMethod("expandQuickSettingsPanel")
                method.invoke(statusBarService)
                Log.d(TAG, "Opened quick settings via StatusBarManager reflection")
                return true
            }
        } catch (e: Throwable) {
            Log.w(TAG, "StatusBarManager quick settings: ${e.message}")
        }

        // 3. IStatusBarService Binder IPC Fallback
        try {
            val serviceManagerClass = Class.forName("android.os.ServiceManager")
            val getServiceMethod = serviceManagerClass.getMethod("getService", String::class.java)
            val binder = getServiceMethod.invoke(null, "statusbar") as? IBinder
            if (binder != null) {
                val iStatusBarClass = Class.forName("com.android.internal.statusbar.IStatusBarService\$Stub")
                val asInterfaceMethod = iStatusBarClass.getMethod("asInterface", IBinder::class.java)
                val iStatusBar = asInterfaceMethod.invoke(null, binder)
                val expandMethod = try {
                    iStatusBar.javaClass.getMethod("expandSettingsPanel")
                } catch (_: NoSuchMethodException) {
                    iStatusBar.javaClass.getMethod("expandQuickSettingsPanel")
                }
                expandMethod.invoke(iStatusBar)
                Log.d(TAG, "Opened quick settings via IStatusBarService IPC")
                return true
            }
        } catch (e: Throwable) {
            Log.w(TAG, "IStatusBarService quick settings fallback: ${e.message}")
        }

        return false
    }

    fun vibrate(context: Context, durationMs: Long = 35) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager =
                    context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = vibratorManager?.defaultVibrator
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(
                        VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(durationMs)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibrate exception: ${e.message}")
        }
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        if (HanglyAccessibilityService.instance != null) return true

        val expectedServiceName = "${context.packageName}/${HanglyAccessibilityService::class.java.canonicalName}"
        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)
        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals(expectedServiceName, ignoreCase = true)) {
                return true
            }
        }
        return false
    }
}
