package space.mathewvarghese.washwatch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class VibrationMonitorService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var settings: Settings
    private val filter = LinearAccelerationFilter()

    // Configuration (reloaded whenever preferences change)
    @Volatile private var appliance = Appliance.DRYER
    @Volatile private var threshold = Settings.DEFAULT_THRESHOLD
    @Volatile private var confirmMillis = Appliance.DRYER.defaultConfirmSeconds * 1000L
    @Volatile private var ntfyTopic: String? = null

    // State machine
    private enum class MachineState { IDLE, RUNNING }
    @Volatile private var machineState = MachineState.IDLE
    /** Last time a single sample exceeded the threshold. Any vibration resets the "off" timer. */
    @Volatile private var lastVibrationAt = 0L
    /** When the windowed RMS first went above the threshold (0 when it is below). */
    @Volatile private var rmsAboveSince = 0L
    /** When the current cycle's vibration began (not when it was confirmed), for reporting how long it ran. */
    private var runningSince = 0L
    /** When monitoring began waiting for the first cycle; 0 once the "not started" alert is no longer armed. */
    private var waitingSince = 0L

    // Sensor data
    private val samples = ArrayDeque<Float>()
    private val sampleWindowSize = 75 // ~1.5 seconds at 50Hz (SENSOR_DELAY_GAME)

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var lastNotificationText = ""

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> loadSettings() }

    companion object {
        private const val TAG = "LaundryAlertService"
        private const val NOTIFICATION_CHANNEL_ID = "VibrationMonitorChannel"
        private const val NOTIFICATION_ID = 1
        private const val ALERT_CHANNEL_ID = "WashWatchAlerts"
        private const val NOT_STARTED_NOTIFICATION_ID = 2
        /** Alert if the machine still hasn't started this long after monitoring began. */
        private const val NOT_STARTED_ALERT_MILLIS = 3 * 60_000L
        /** RMS must stay above the threshold this long before a cycle counts as started. */
        private const val START_CONFIRM_MILLIS = 10_000L

        private val _isRunning = MutableStateFlow(false)
        val isRunning = _isRunning.asStateFlow()

        private val _status = MutableStateFlow("Monitoring stopped")
        val status = _status.asStateFlow()

        private val _rms = MutableStateFlow(0f)
        val rms = _rms.asStateFlow()
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        settings = Settings(this)
        settings.prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification("Waiting for the machine to start…"))
        if (!_isRunning.value) {
            _isRunning.value = true
            loadSettings()
            machineState = MachineState.IDLE
            waitingSince = SystemClock.elapsedRealtime()
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
            serviceScope.launch { tickLoop() }
            Log.d(TAG, "Service started and is now in foreground.")
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        _isRunning.value = false
        _status.value = "Monitoring stopped"
        sensorManager.unregisterListener(this)
        settings.prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        serviceScope.cancel()
        Log.d(TAG, "Service destroyed.")
    }

    private fun loadSettings() {
        val selected = settings.appliance ?: Appliance.DRYER
        appliance = selected
        threshold = settings.threshold(selected) ?: Settings.DEFAULT_THRESHOLD
        confirmMillis = settings.confirmSeconds(selected) * 1000L
        ntfyTopic = settings.ntfyTopic
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { /* Not used */ }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val magnitude = filter.magnitude(event.values)
        val now = SystemClock.elapsedRealtime()

        if (magnitude > threshold) lastVibrationAt = now

        if (samples.size >= sampleWindowSize) samples.removeFirst()
        samples.addLast(magnitude)
        if (samples.size < sampleWindowSize) return

        val rms = sqrt(samples.sumOf { (it * it).toDouble() } / samples.size).toFloat()
        _rms.value = rms
        rmsAboveSince = if (rms > threshold) (rmsAboveSince.takeIf { it != 0L } ?: now) else 0L
    }

    /** Evaluates the state machine twice a second so the status text stays fresh. */
    private suspend fun tickLoop() {
        while (serviceScope.isActive) {
            val now = SystemClock.elapsedRealtime()
            when (machineState) {
                MachineState.IDLE -> {
                    val since = rmsAboveSince
                    if (since != 0L && now - since >= START_CONFIRM_MILLIS) {
                        machineState = MachineState.RUNNING
                        lastVibrationAt = now
                        // Count from when the vibration began, not from the end of the confirmation wait.
                        runningSince = since
                        // Only the first wait after starting monitoring is watched, so a finished cycle never triggers it.
                        waitingSince = 0L
                        getSystemService(NotificationManager::class.java).cancel(NOT_STARTED_NOTIFICATION_ID)
                        Log.d(TAG, "${appliance.label} started.")
                        sendNtfy("${appliance.label} started", "Vibration detected — monitoring the cycle.", priority = 2, tags = listOf(appliance.key, "arrow_forward"))
                        updateStatus("${appliance.label} running")
                    } else if (since != 0L) {
                        val remaining = (START_CONFIRM_MILLIS - (now - since)) / 1000 + 1
                        updateStatus("Vibration detected — confirming start in ${remaining}s")
                    } else {
                        updateStatus("Waiting for the ${appliance.label.lowercase()} to start…")
                    }
                    if (waitingSince != 0L && now - waitingSince >= NOT_STARTED_ALERT_MILLIS) {
                        waitingSince = 0L
                        sendNotStartedAlert()
                    }
                }
                MachineState.RUNNING -> {
                    // Read once: the sensor thread keeps updating it, and the check and the reported duration must agree.
                    val lastVibration = lastVibrationAt
                    val quietFor = now - lastVibration
                    if (quietFor >= confirmMillis) {
                        machineState = MachineState.IDLE
                        rmsAboveSince = 0L
                        // The cycle ended at the last vibration, not after the quiet period that confirmed it.
                        val ranFor = formatDuration((lastVibration - runningSince) / 1000)
                        Log.d(TAG, "${appliance.label} finished after $ranFor.")
                        sendNtfy(
                            "${appliance.label} done!",
                            "Ran for $ranFor.",
                            priority = 4,
                            tags = listOf(appliance.key, "white_check_mark", "basket"), // First tag names the machine for WashWatch Alerts.
                        )
                        updateStatus("${appliance.label} finished! Ran for $ranFor.")
                        delay(5000) // Let the "finished" message linger before going back to waiting.
                    } else if (quietFor < 1000) {
                        updateStatus("${appliance.label} running for ${formatDuration((now - runningSince) / 1000)} — vibrating")
                    } else {
                        val remaining = (confirmMillis - quietFor) / 1000 + 1
                        updateStatus("Still for ${quietFor / 1000}s — done in ${formatDuration(remaining)} if it stays still")
                    }
                }
            }
            delay(500)
        }
    }

    private fun sendNtfy(title: String, message: String, priority: Int, tags: List<String>) {
        val topic = ntfyTopic ?: return
        serviceScope.launch { Ntfy.publish(topic, title, message, priority, tags) }
    }

    private fun sendNotStartedAlert() {
        val minutes = NOT_STARTED_ALERT_MILLIS / 60_000
        val title = "${appliance.label} hasn't started"
        val message = "No vibration for $minutes minutes since monitoring began. Did you press start?"
        Log.d(TAG, "${appliance.label} not started after $minutes minutes. Sending alert.")
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(Notification.BigTextStyle().bigText(message))
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOT_STARTED_NOTIFICATION_ID, notification)
        sendNtfy(title, message, priority = 4, tags = listOf(appliance.key, "warning"))
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(NOTIFICATION_CHANNEL_ID, "Laundry Alert monitoring", NotificationManager.IMPORTANCE_LOW)
        )
        manager.createNotificationChannel(
            NotificationChannel(ALERT_CHANNEL_ID, "Laundry alerts", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    private fun createNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateStatus(message: String) {
        _status.value = message
        if (message == lastNotificationText) return
        lastNotificationText = message
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, createNotification(message))
    }
}

fun formatDuration(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = totalSeconds % 3600 / 60
    val s = totalSeconds % 60
    return when {
        h > 0 -> "${h}h ${m}m ${s}s"
        m > 0 -> "${m}m ${s}s"
        else -> "${s}s"
    }
}
