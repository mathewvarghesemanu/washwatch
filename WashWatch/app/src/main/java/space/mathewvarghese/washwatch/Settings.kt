package space.mathewvarghese.washwatch

import android.content.Context
import android.content.SharedPreferences
import kotlin.math.sqrt

enum class Appliance(val key: String, val label: String, val defaultConfirmSeconds: Int) {
    WASHER("washing-machine", "Washing Machine", 180),
    DRYER("dryer", "Dryer", 60);

    companion object {
        fun fromKey(key: String?): Appliance? = entries.firstOrNull { it.key == key }
    }
}

/** Thin wrapper around SharedPreferences shared by the activity and the service. */
class Settings(context: Context) {
    val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var appliance: Appliance?
        get() = Appliance.fromKey(prefs.getString(KEY_APPLIANCE, null))
        set(value) = prefs.edit().putString(KEY_APPLIANCE, value?.key).apply()

    fun confirmSeconds(appliance: Appliance): Int =
        prefs.getInt("${appliance.key}_confirm_sec", appliance.defaultConfirmSeconds)

    fun setConfirmSeconds(appliance: Appliance, seconds: Int) =
        prefs.edit().putInt("${appliance.key}_confirm_sec", seconds).apply()

    /**
     * Strongest vibration (m/s²) measured with the machine off; null until calibrated.
     * Builds before the multiplier setting stored only the threshold, made with the old fixed 3×, so undo that.
     */
    fun baseline(appliance: Appliance): Float? {
        val key = "${appliance.key}_baseline"
        val legacyKey = "${appliance.key}_threshold"
        return when {
            prefs.contains(key) -> prefs.getFloat(key, 0f)
            prefs.contains(legacyKey) -> prefs.getFloat(legacyKey, DEFAULT_THRESHOLD) / DEFAULT_MULTIPLIER
            else -> null
        }
    }

    fun setBaseline(appliance: Appliance, value: Float) =
        prefs.edit().putFloat("${appliance.key}_baseline", value).remove("${appliance.key}_threshold").apply()

    /** How many times the calibrated baseline vibration must be exceeded to count as running. */
    fun multiplier(appliance: Appliance): Float =
        prefs.getFloat("${appliance.key}_multiplier", DEFAULT_MULTIPLIER)

    fun setMultiplier(appliance: Appliance, value: Float) =
        prefs.edit().putFloat("${appliance.key}_multiplier", value).apply()

    /** Vibration threshold in m/s² (baseline × multiplier); null until the user has measured the "off" baseline. */
    fun threshold(appliance: Appliance): Float? =
        baseline(appliance)?.let { (it * multiplier(appliance)).coerceAtLeast(MIN_THRESHOLD) }

    /** ntfy topic the paired phone subscribes to; null when no phone is paired. */
    var ntfyTopic: String?
        get() = prefs.getString(KEY_NTFY_TOPIC, null)
        set(value) = prefs.edit().putString(KEY_NTFY_TOPIC, value).apply()

    /** App theme; dark unless the user switched to light in settings. */
    var darkMode: Boolean
        get() = prefs.getBoolean(KEY_DARK_MODE, true)
        set(value) = prefs.edit().putBoolean(KEY_DARK_MODE, value).apply()

    companion object {
        private const val PREFS_NAME = "WashWatchPrefs"
        private const val KEY_NTFY_TOPIC = "ntfy_topic"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_APPLIANCE = "appliance"
        const val DEFAULT_THRESHOLD = 0.15f
        /** Floor so a perfectly still sensor (e.g. an emulator) doesn't yield a zero threshold. */
        const val MIN_THRESHOLD = 0.02f
        const val DEFAULT_MULTIPLIER = 3f
        const val MIN_MULTIPLIER = 1.5f
        const val MAX_MULTIPLIER = 10f
    }
}

/** Strips gravity from raw accelerometer readings and returns the linear acceleration magnitude. */
class LinearAccelerationFilter(private val alpha: Float = 0.8f) {
    private val gravity = FloatArray(3)
    private var initialized = false

    fun magnitude(values: FloatArray): Float {
        if (!initialized) {
            values.copyInto(gravity, endIndex = 3)
            initialized = true
        }
        var sum = 0f
        for (i in 0..2) {
            gravity[i] = alpha * gravity[i] + (1 - alpha) * values[i]
            val linear = values[i] - gravity[i]
            sum += linear * linear
        }
        return sqrt(sum)
    }
}
