package space.mathewvarghese.washwatch

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom

/** Push notifications to a paired phone via ntfy (https://ntfy.sh). */
object Ntfy {
    private const val TAG = "LaundryAlertNtfy"
    const val SERVER = "https://ntfy.sh"
    private const val TOPIC_ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789"

    /** Topics on ntfy.sh are public to anyone who knows the name, so make it unguessable. */
    fun randomTopic(): String {
        val random = SecureRandom()
        return "washwatch-" + (1..16).map { TOPIC_ALPHABET[random.nextInt(TOPIC_ALPHABET.length)] }.joinToString("")
    }

    /** Deep link that opens the ntfy app's "subscribe to topic" screen when scanned. */
    fun subscribeLink(topic: String): String = "ntfy://${SERVER.removePrefix("https://")}/$topic"

    /** Plain web link, for sharing or for phones without the ntfy app. */
    fun webLink(topic: String): String = "$SERVER/$topic"

    /** Publishes via ntfy's JSON API (POST to the server root), which handles emoji in titles. Returns true on 2xx. */
    suspend fun publish(
        topic: String,
        title: String,
        message: String,
        priority: Int = 3,
        tags: List<String> = emptyList(),
    ): Boolean = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("topic", topic)
            put("title", title)
            put("message", message)
            put("priority", priority)
            if (tags.isNotEmpty()) put("tags", JSONArray(tags))
        }.toString()
        try {
            val connection = URL(SERVER).openConnection() as HttpURLConnection
            connection.run {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                doOutput = true
                connectTimeout = 5000
                readTimeout = 5000
                OutputStreamWriter(outputStream, "UTF-8").use { it.write(body) }
                val code = responseCode
                Log.d(TAG, "ntfy publish to $topic response code: $code")
                disconnect()
                code in 200..299
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error publishing to ntfy topic $topic", e)
            false
        }
    }

    fun qrBitmap(content: String, sizePx: Int = 640): Bitmap {
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, mapOf(EncodeHintType.MARGIN to 1))
        val pixels = IntArray(sizePx * sizePx) { i ->
            if (matrix[i % sizePx, i / sizePx]) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(pixels, sizePx, sizePx, Bitmap.Config.ARGB_8888)
    }
}
