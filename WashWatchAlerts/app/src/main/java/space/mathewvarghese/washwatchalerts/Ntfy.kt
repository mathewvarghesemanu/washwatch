package space.mathewvarghese.washwatchalerts

import android.net.Uri
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Where alerts come from: an ntfy server plus the private topic the WashWatch host publishes to. */
data class Subscription(val server: String, val topic: String) {
    val streamUrl: String get() = "$server/$topic/json"
}

/** Which appliance a host is watching. Keys match the tag the host sends (its `Appliance.key`). */
enum class Machine(val key: String, val label: String, val emoji: String) {
    WASHER("washing-machine", "Washing machine", "🫧"),
    DRYER("dryer", "Dryer", "🌀"),
}

/** What an alert is about, from the host's tags. Each kind gets its own notification channel per machine. */
enum class AlertKind(val tag: String, val channelSuffix: String) {
    DONE("white_check_mark", "done"),
    STARTED("arrow_forward", "started"),
    WARNING("warning", "warning"),
}

/** One message published by the WashWatch host. */
data class Alert(
    val id: String,
    val time: Long,
    val title: String,
    val message: String,
    val priority: Int,
    val tags: List<String>,
) {
    /**
     * The machine this alert is about, from the host's machine tag. Older host builds don't send
     * that tag, so fall back to the "Washing Machine …" / "Dryer …" title they always used.
     */
    val machine: Machine?
        get() = Machine.entries.firstOrNull { it.key in tags }
            ?: Machine.entries.firstOrNull { title.startsWith(it.label, ignoreCase = true) }

    /** Null for messages that aren't about a cycle, like the host's "paired" test message. */
    val kind: AlertKind?
        get() = AlertKind.entries.firstOrNull { it.tag in tags }

    /** Title with the machine's emoji, then tag emoji, in front (the way the ntfy app shows tags). */
    val displayTitle: String
        get() {
            val emoji = (listOfNotNull(machine?.emoji) + tags.mapNotNull { Ntfy.TAG_EMOJI[it] }).joinToString("")
            val base = title.ifBlank { "WashWatch" }
            return if (emoji.isEmpty()) base else "$emoji $base"
        }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("time", time)
        put("title", title)
        put("message", message)
        put("priority", priority)
        put("tags", org.json.JSONArray(tags))
    }

    companion object {
        fun fromJson(json: JSONObject): Alert = Alert(
            id = json.getString("id"),
            time = json.optLong("time"),
            title = json.optString("title"),
            message = json.optString("message"),
            priority = json.optInt("priority", 3),
            tags = json.optJSONArray("tags")?.let { array -> (0 until array.length()).map { array.getString(it) } }
                ?: emptyList(),
        )
    }
}

/** Receives push alerts from ntfy (https://ntfy.sh) on the topic the WashWatch host paired us with. */
object Ntfy {
    private const val TAG = "WashWatchAlertsNtfy"
    const val DEFAULT_SERVER = "https://ntfy.sh"
    private val TOPIC_PATTERN = Regex("^[-_A-Za-z0-9]{1,64}$")

    /** ntfy sends a keepalive every 45s; anything much longer than that means the connection is dead. */
    private const val READ_TIMEOUT_MILLIS = 90_000

    /** The emoji for every tag the WashWatch host sends, plus a few common ntfy ones. */
    val TAG_EMOJI = mapOf(
        "white_check_mark" to "✅",
        "basket" to "🧺",
        "arrow_forward" to "▶️",
        "tada" to "🎉",
        "warning" to "⚠️",
        "bell" to "🔔",
    )

    /**
     * Parses what the host's QR code (or share link) contains. Accepts:
     *  - `ntfy://ntfy.sh/washwatch-abc…` (the QR code)
     *  - `https://ntfy.sh/washwatch-abc…` (web link)
     *  - `washwatch-abc…` (the pairing code typed by hand)
     */
    fun parsePairing(raw: String): Subscription? {
        val text = raw.trim()
        if (TOPIC_PATTERN.matches(text)) return Subscription(DEFAULT_SERVER, text)

        val uri = runCatching { Uri.parse(text) }.getOrNull() ?: return null
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
        val topic = uri.pathSegments.singleOrNull()?.takeIf { TOPIC_PATTERN.matches(it) } ?: return null
        val port = if (uri.port != -1) ":${uri.port}" else ""
        val server = when (uri.scheme?.lowercase()) {
            // ntfy:// links mean https unless the link explicitly opts out, same as the ntfy app.
            "ntfy" -> (if (uri.getQueryParameter("secure") == "false") "http://" else "https://") + host + port
            "https", "http" -> "${uri.scheme!!.lowercase()}://$host$port"
            else -> return null
        }
        return Subscription(server, topic)
    }

    /**
     * Opens the JSON stream and blocks, calling [onOpen] once connected and [onAlert] for every message,
     * until the connection drops (throws) or is closed through [onConnection].
     * [sinceSeconds] asks the server to replay messages published since then (it caches ~12h).
     */
    fun stream(
        subscription: Subscription,
        sinceSeconds: Long?,
        onConnection: (HttpURLConnection) -> Unit,
        onOpen: () -> Unit,
        onAlert: (Alert) -> Unit,
    ) {
        val url = subscription.streamUrl + (sinceSeconds?.let { "?since=$it" } ?: "")
        val connection = URL(url).openConnection() as HttpURLConnection
        onConnection(connection)
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.setRequestProperty("Accept", "application/x-ndjson")
            val code = connection.responseCode
            if (code !in 200..299) throw java.io.IOException("ntfy responded with HTTP $code")
            connection.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (line in lines) {
                    if (line.isBlank()) continue
                    val json = runCatching { JSONObject(line) }.getOrNull() ?: continue
                    when (json.optString("event")) {
                        "open" -> onOpen()
                        "message" -> onAlert(Alert.fromJson(json))
                        // "keepalive" just resets the read timeout.
                    }
                }
            }
            throw java.io.IOException("ntfy closed the stream")
        } catch (e: Exception) {
            Log.w(TAG, "Stream for ${subscription.topic} ended: ${e.message}")
            throw e
        } finally {
            connection.disconnect()
        }
    }
}
