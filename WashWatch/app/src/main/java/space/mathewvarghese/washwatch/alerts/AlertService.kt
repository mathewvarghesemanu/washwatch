package space.mathewvarghese.washwatch.alerts

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection

enum class ConnectionState { STOPPED, CONNECTING, CONNECTED, OFFLINE }

/** Live state the UI observes; the service is the only writer. */
object AlertState {
    private val _connection = MutableStateFlow(ConnectionState.STOPPED)
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    private val _history = MutableStateFlow<List<Alert>>(emptyList())
    val history: StateFlow<List<Alert>> = _history.asStateFlow()

    internal fun setConnection(state: ConnectionState) { _connection.value = state }
    fun setHistory(alerts: List<Alert>) { _history.value = alerts }
}

/** Keeps a streaming connection to the paired ntfy topic and turns each message into a notification. */
class AlertService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var settings: Settings
    private var streamJob: Job? = null
    @Volatile private var connection: HttpURLConnection? = null

    /** Signalled when the network comes back so a waiting reconnect happens immediately. */
    private val wake = Channel<Unit>(Channel.CONFLATED)

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { wake.trySend(Unit) }
        // Drop the socket right away rather than waiting up to 90s for the read to time out.
        override fun onLost(network: Network) { connection?.disconnect() }
    }

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        Notifications.createChannels(this)
        getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(networkCallback)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this,
            Notifications.SERVICE_NOTIFICATION_ID,
            Notifications.serviceNotification(this, AlertState.connection.value),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        val subscription = settings.subscription
        if (subscription == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        // A restart means the pairing changed, so drop the old stream and open a new one.
        if (intent?.action == ACTION_RESTART || streamJob?.isActive != true) {
            streamJob?.cancel()
            connection?.disconnect()
            streamJob = serviceScope.launch { streamLoop(subscription) }
        }
        return START_STICKY
    }

    private suspend fun streamLoop(subscription: Subscription) {
        var backoff = MIN_BACKOFF_MILLIS
        while (currentCoroutineContext().isActive) {
            setConnection(ConnectionState.CONNECTING)
            try {
                Ntfy.stream(
                    subscription,
                    settings.lastSeenSeconds,
                    onConnection = { connection = it },
                    onOpen = {
                        backoff = MIN_BACKOFF_MILLIS
                        setConnection(ConnectionState.CONNECTED)
                    },
                    onAlert = { handleAlert(subscription, it) },
                )
            } catch (e: Exception) {
                Log.w(TAG, "Disconnected from ntfy: ${e.message}")
            }
            if (!currentCoroutineContext().isActive) break
            setConnection(ConnectionState.OFFLINE)
            withTimeoutOrNull(backoff) { wake.receive() }
            backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MILLIS)
        }
    }

    @Synchronized
    private fun handleAlert(subscription: Subscription, alert: Alert) {
        if (settings.subscription != subscription) return // Unpaired or re-paired while this stream was open.
        val history = settings.history
        if (history.any { it.id == alert.id }) return // Replayed after a reconnect; already shown.
        val updated = (listOf(alert) + history).sortedByDescending { it.time }
        settings.history = updated
        AlertState.setHistory(settings.history)
        settings.lastSeenSeconds = maxOf(settings.lastSeenSeconds ?: 0L, alert.time)
        val machine = alert.machine
        if (machine != null && alert.kind == AlertKind.DONE) Notifications.cancelCycle(this, machine)
        Notifications.showAlert(
            this,
            alert,
            lastCycle = machine?.let { Cycles.lastCycle(updated, it) },
            started = if (alert.kind == AlertKind.DONE) Cycles.startedFor(updated, alert) else null,
        )
    }

    private fun setConnection(state: ConnectionState) {
        AlertState.setConnection(state)
        Notifications.updateServiceNotification(this, state)
    }

    override fun onDestroy() {
        getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(networkCallback)
        serviceScope.cancel()
        connection?.disconnect()
        AlertState.setConnection(ConnectionState.STOPPED)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "WashWatchAlertService"
        private const val ACTION_RESTART = "space.mathewvarghese.washwatch.alerts.RESTART"
        private const val MIN_BACKOFF_MILLIS = 2_000L
        private const val MAX_BACKOFF_MILLIS = 60_000L

        /** Starts listening, or keeps listening if already running. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, AlertService::class.java))
        }

        /** Reconnects with the current pairing (call after pairing with a new host). */
        fun restart(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AlertService::class.java).setAction(ACTION_RESTART),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AlertService::class.java))
        }
    }
}
