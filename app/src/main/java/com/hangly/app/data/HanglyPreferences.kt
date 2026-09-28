package com.hangly.app.data

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color

class HanglyPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "hangly_settings"

        const val KEY_ENABLED = "key_enabled"
        const val KEY_SELECTED_CHARM = "key_selected_charm"
        const val KEY_CUSTOM_IMAGE_PATH = "key_custom_image_path"
        const val KEY_CUSTOM_CROP_SHAPE = "key_custom_crop_shape"
        const val KEY_HORIZONTAL_PERCENT = "key_horizontal_percent"
        const val KEY_ROPE_LENGTH_DP = "key_rope_length_dp"
        const val KEY_ROPE_THICKNESS_DP = "key_rope_thickness_dp"
        const val KEY_ROPE_STYLE = "key_rope_style"
        const val KEY_ROPE_COLOR = "key_rope_color"
        const val KEY_CHARM_SIZE_DP = "key_charm_size_dp"
        const val KEY_GRAVITY_PERCENT = "key_gravity_percent"
        const val KEY_TILT_PHYSICS = "key_tilt_physics"

        // Charm Presets
        const val CHARM_SPIDERMAN = "spiderman"
        const val CHARM_EVIL_EYE = "evil_eye"
        const val CHARM_CAP_SHIELD = "cap_shield"
        const val CHARM_LUCKY_CAT = "lucky_cat"
        const val CHARM_GHOST = "ghost"
        const val CHARM_CUSTOM = "custom"

        // Rope Styles
        const val ROPE_STYLE_CLASSIC = "classic"
        const val ROPE_STYLE_CHAIN = "chain"
        const val ROPE_STYLE_WEB = "web"
        const val ROPE_STYLE_BEADS = "beads"
        const val ROPE_STYLE_BRAIDED = "braided"
        const val ROPE_STYLE_RIBBON = "braided" // Backward compatibility alias

        // Crop Shapes
        const val SHAPE_CIRCLE = "circle"
        const val SHAPE_HEART = "heart"
        const val SHAPE_SQUARE = "square"
        const val SHAPE_STAR = "star"
        const val SHAPE_SHIELD = "shield"

        // Theme Modes
        const val KEY_THEME_MODE = "key_theme_mode"
        const val THEME_SYSTEM = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2

        // Connector Styles (Connecting rope to image)
        const val KEY_CONNECTOR_STYLE = "key_connector_style"
        const val CONNECTOR_CRESCENT = "crescent"
        const val CONNECTOR_RING = "ring"
        const val DEFAULT_CONNECTOR_STYLE = CONNECTOR_CRESCENT

        // Default Values
        const val DEFAULT_GRAVITY_PERCENT = 75f // 75% of normal gravity as requested
        const val DEFAULT_ROPE_LENGTH_DP = 130f
        const val DEFAULT_ROPE_THICKNESS_DP = 4f
        const val DEFAULT_CHARM_SIZE_DP = 68f
        const val DEFAULT_HORIZONTAL_PERCENT = 0.50f
        const val DEFAULT_ROPE_STYLE = ROPE_STYLE_CLASSIC
        const val DEFAULT_CROP_SHAPE = SHAPE_CIRCLE
        val DEFAULT_ROPE_COLOR = Color.parseColor("#212121")
    }

    var isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var selectedCharm: String
        get() = prefs.getString(KEY_SELECTED_CHARM, CHARM_SPIDERMAN) ?: CHARM_SPIDERMAN
        set(value) = prefs.edit().putString(KEY_SELECTED_CHARM, value).apply()

    var customImagePath: String?
        get() = prefs.getString(KEY_CUSTOM_IMAGE_PATH, null)
        set(value) = prefs.edit().putString(KEY_CUSTOM_IMAGE_PATH, value).apply()

    var customCropShape: String
        get() = prefs.getString(KEY_CUSTOM_CROP_SHAPE, DEFAULT_CROP_SHAPE) ?: DEFAULT_CROP_SHAPE
        set(value) = prefs.edit().putString(KEY_CUSTOM_CROP_SHAPE, value).apply()

    var horizontalPercent: Float
        get() = prefs.getFloat(KEY_HORIZONTAL_PERCENT, DEFAULT_HORIZONTAL_PERCENT)
        set(value) = prefs.edit().putFloat(KEY_HORIZONTAL_PERCENT, value).apply()

    var ropeLengthDp: Float
        get() = prefs.getFloat(KEY_ROPE_LENGTH_DP, DEFAULT_ROPE_LENGTH_DP)
        set(value) = prefs.edit().putFloat(KEY_ROPE_LENGTH_DP, value).apply()

    var ropeThicknessDp: Float
        get() = prefs.getFloat(KEY_ROPE_THICKNESS_DP, DEFAULT_ROPE_THICKNESS_DP)
        set(value) = prefs.edit().putFloat(KEY_ROPE_THICKNESS_DP, value).apply()

    var ropeStyle: String
        get() = prefs.getString(KEY_ROPE_STYLE, DEFAULT_ROPE_STYLE) ?: DEFAULT_ROPE_STYLE
        set(value) = prefs.edit().putString(KEY_ROPE_STYLE, value).apply()

    var ropeColor: Int
        get() = prefs.getInt(KEY_ROPE_COLOR, DEFAULT_ROPE_COLOR)
        set(value) = prefs.edit().putInt(KEY_ROPE_COLOR, value).apply()

    var charmSizeDp: Float
        get() = prefs.getFloat(KEY_CHARM_SIZE_DP, DEFAULT_CHARM_SIZE_DP)
        set(value) = prefs.edit().putFloat(KEY_CHARM_SIZE_DP, value).apply()

    var gravityPercent: Float
        get() = prefs.getFloat(KEY_GRAVITY_PERCENT, DEFAULT_GRAVITY_PERCENT)
        set(value) = prefs.edit().putFloat(KEY_GRAVITY_PERCENT, value).apply()

    var isTiltPhysicsEnabled: Boolean
        get() = prefs.getBoolean(KEY_TILT_PHYSICS, true)
        set(value) = prefs.edit().putBoolean(KEY_TILT_PHYSICS, value).apply()

    var themeMode: Int
        get() = prefs.getInt(KEY_THEME_MODE, THEME_SYSTEM)
        set(value) = prefs.edit().putInt(KEY_THEME_MODE, value).apply()

    var connectorStyle: String
        get() = prefs.getString(KEY_CONNECTOR_STYLE, DEFAULT_CONNECTOR_STYLE) ?: DEFAULT_CONNECTOR_STYLE
        set(value) = prefs.edit().putString(KEY_CONNECTOR_STYLE, value).apply()

    fun resetToDefaults() {
        prefs.edit().clear()
            .putBoolean(KEY_ENABLED, true)
            .putString(KEY_SELECTED_CHARM, CHARM_SPIDERMAN)
            .putString(KEY_ROPE_STYLE, DEFAULT_ROPE_STYLE)
            .putString(KEY_CONNECTOR_STYLE, DEFAULT_CONNECTOR_STYLE)
            .putString(KEY_CUSTOM_CROP_SHAPE, DEFAULT_CROP_SHAPE)
            .putFloat(KEY_HORIZONTAL_PERCENT, DEFAULT_HORIZONTAL_PERCENT)
            .putFloat(KEY_ROPE_LENGTH_DP, DEFAULT_ROPE_LENGTH_DP)
            .putFloat(KEY_ROPE_THICKNESS_DP, DEFAULT_ROPE_THICKNESS_DP)
            .putInt(KEY_ROPE_COLOR, DEFAULT_ROPE_COLOR)
            .putFloat(KEY_CHARM_SIZE_DP, DEFAULT_CHARM_SIZE_DP)
            .putFloat(KEY_GRAVITY_PERCENT, DEFAULT_GRAVITY_PERCENT)
            .putBoolean(KEY_TILT_PHYSICS, true)
            .apply()
    }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }
}
