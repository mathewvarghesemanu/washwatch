package space.mathewvarghese.washwatchalerts

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import org.json.JSONArray

/** How "cycle done" alerts get your attention. A null [sound] means silent. */
data class DoneAlertStyle(val sound: Uri?, val vibrate: Boolean, val ringLikeAlarm: Boolean)

/** Thin wrapper around SharedPreferences shared by the activity, the service and the boot receiver. */
class Settings(context: Context) {
    val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** The host this phone is paired with; null until a QR code has been scanned. */
    var subscription: Subscription?
        get() {
            val topic = prefs.getString(KEY_TOPIC, null) ?: return null
            return Subscription(prefs.getString(KEY_SERVER, null) ?: Ntfy.DEFAULT_SERVER, topic)
        }
        set(value) {
            prefs.edit()
                .putString(KEY_SERVER, value?.server)
                .putString(KEY_TOPIC, value?.topic)
                .apply()
        }

    /** Unix seconds of the newest alert seen, so a reconnect replays only what was missed. */
    var lastSeenSeconds: Long?
        get() = if (prefs.contains(KEY_LAST_SEEN)) prefs.getLong(KEY_LAST_SEEN, 0L) else null
        set(value) {
            prefs.edit().apply { if (value == null) remove(KEY_LAST_SEEN) else putLong(KEY_LAST_SEEN, value) }.apply()
        }

    /** Recent alerts, newest first. */
    var history: List<Alert>
        get() {
            val stored = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
            return runCatching {
                val array = JSONArray(stored)
                (0 until array.length()).map { Alert.fromJson(array.getJSONObject(it)) }
            }.getOrDefault(emptyList())
        }
        set(value) {
            val array = JSONArray().apply { value.take(MAX_HISTORY).forEach { put(it.toJson()) } }
            prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
        }

    /** The app opens in the dark theme unless switched off in settings. */
    var darkMode: Boolean
        get() = prefs.getBoolean(KEY_DARK_MODE, true)
        set(value) = prefs.edit().putBoolean(KEY_DARK_MODE, value).apply()

    /** Defaults to the phone's notification sound with vibration, played once. */
    val doneAlertStyle: DoneAlertStyle
        get() = DoneAlertStyle(
            sound = when (val stored = prefs.getString(KEY_DONE_SOUND, null)) {
                null -> android.provider.Settings.System.DEFAULT_NOTIFICATION_URI
                "" -> null
                else -> Uri.parse(stored)
            },
            vibrate = prefs.getBoolean(KEY_DONE_VIBRATE, true),
            ringLikeAlarm = prefs.getBoolean(KEY_DONE_ALARM, false),
        )

    /**
     * Bumped on every style change. Android freezes a channel's sound and vibration once it exists,
     * so the "done" channels are recreated under a new ID that includes this.
     */
    val doneChannelVersion: Int
        get() = prefs.getInt(KEY_DONE_CHANNEL_VERSION, 0)

    fun setDoneAlertStyle(style: DoneAlertStyle) {
        prefs.edit()
            .putString(KEY_DONE_SOUND, style.sound?.toString() ?: "")
            .putBoolean(KEY_DONE_VIBRATE, style.vibrate)
            .putBoolean(KEY_DONE_ALARM, style.ringLikeAlarm)
            .putInt(KEY_DONE_CHANNEL_VERSION, doneChannelVersion + 1)
            .apply()
    }

    /** Pairs with a new host: replays nothing older than now and forgets the old host's alerts. */
    fun pair(subscription: Subscription) {
        this.subscription = subscription
        lastSeenSeconds = System.currentTimeMillis() / 1000
        history = emptyList()
    }

    fun unpair() {
        subscription = null
        lastSeenSeconds = null
        history = emptyList()
    }

    companion object {
        private const val PREFS_NAME = "WashWatchAlertsPrefs"
        private const val KEY_SERVER = "ntfy_server"
        private const val KEY_TOPIC = "ntfy_topic"
        private const val KEY_LAST_SEEN = "last_seen_seconds"
        private const val KEY_HISTORY = "history"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_DONE_SOUND = "done_sound"
        private const val KEY_DONE_VIBRATE = "done_vibrate"
        private const val KEY_DONE_ALARM = "done_ring_like_alarm"
        private const val KEY_DONE_CHANNEL_VERSION = "done_channel_version"
        private const val MAX_HISTORY = 50
    }
}
