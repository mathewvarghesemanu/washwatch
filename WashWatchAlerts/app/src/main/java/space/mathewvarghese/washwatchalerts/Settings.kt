package space.mathewvarghese.washwatchalerts

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

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
        private const val MAX_HISTORY = 50
    }
}
