package space.mathewvarghese.washwatchalerts

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Build
import android.text.format.DateUtils
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Notification channels and builders. Each machine gets its own "done", "started" and "hasn't started"
 * channels (grouped under the machine in Android's settings) so they can have different sounds. Alerts with no
 * machine or kind (e.g. the host's test message) fall back to generic channels picked by ntfy priority (1–5).
 * The "done" channels follow the sound and vibration picked in the app's settings ([DoneAlertStyle]).
 */
object Notifications {
    const val SERVICE_NOTIFICATION_ID = 1
    private const val CHANNEL_SERVICE = "connection"
    private const val CHANNEL_HIGH = "alerts_high"
    private const val CHANNEL_DEFAULT = "alerts_default"
    private const val CHANNEL_LOW = "alerts_low"

    private val DONE_VIBRATION = longArrayOf(0, 600, 300, 600)

    /** The "done" channel IDs carry [Settings.doneChannelVersion]: changing the style means a new channel. */
    private fun channelId(machine: Machine, kind: AlertKind, settings: Settings): String {
        val base = "${machine.key}_${kind.channelSuffix}"
        return if (kind == AlertKind.DONE) "${base}_v${settings.doneChannelVersion}" else base
    }

    /** One notification per machine for a running cycle, so its timer is replaced by the next start and cleared by "done". */
    private fun cycleNotificationId(machine: Machine) = "cycle_${machine.key}".hashCode()

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val settings = Settings(context)
        val style = settings.doneAlertStyle
        val doneChannels = Machine.entries.map { channelId(it, AlertKind.DONE, settings) }.toSet()
        // Drop "done" channels left over from earlier styles (including the unversioned ones from 1.0).
        manager.notificationChannels
            .filter { channel ->
                channel.id !in doneChannels &&
                    Machine.entries.any { channel.id.startsWith("${it.key}_${AlertKind.DONE.channelSuffix}") }
            }
            .forEach { manager.deleteNotificationChannel(it.id) }
        manager.createNotificationChannelGroups(Machine.entries.map { NotificationChannelGroup(it.key, it.label) })
        manager.createNotificationChannels(
            Machine.entries.flatMap { machine ->
                listOf(
                    NotificationChannel(channelId(machine, AlertKind.DONE, settings), "${machine.label} done", NotificationManager.IMPORTANCE_HIGH).apply {
                        description = "When the ${machine.label.lowercase()} finishes its cycle. Change the sound in WashWatch Alerts settings."
                        group = machine.key
                        setSound(
                            style.sound,
                            AudioAttributes.Builder()
                                // Alarm usage plays at alarm volume, so it is heard even with notifications turned down.
                                .setUsage(if (style.ringLikeAlarm) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION_EVENT)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build(),
                        )
                        enableVibration(style.vibrate)
                        if (style.vibrate) vibrationPattern = DONE_VIBRATION
                    },
                    NotificationChannel(channelId(machine, AlertKind.STARTED, settings), "${machine.label} started", NotificationManager.IMPORTANCE_LOW).apply {
                        description = "Quiet heads-up with a running timer when the ${machine.label.lowercase()} starts."
                        group = machine.key
                    },
                    NotificationChannel(channelId(machine, AlertKind.WARNING, settings), "${machine.label} hasn't started", NotificationManager.IMPORTANCE_HIGH).apply {
                        description = "When the ${machine.label.lowercase()} is being watched but never started vibrating."
                        group = machine.key
                        enableVibration(true)
                    },
                )
            } + listOf(
                NotificationChannel(CHANNEL_HIGH, "Cycle finished", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "\"Done!\" alerts and other high-priority messages from WashWatch."
                    enableVibration(true)
                },
                NotificationChannel(CHANNEL_DEFAULT, "Other alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Test messages and normal-priority alerts."
                },
                NotificationChannel(CHANNEL_LOW, "Cycle started", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Quiet heads-up when the machine starts running."
                },
                NotificationChannel(CHANNEL_SERVICE, "Connection status", NotificationManager.IMPORTANCE_MIN).apply {
                    description = "Required by Android to keep listening for alerts in the background."
                    setShowBadge(false)
                },
            )
        )
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun serviceNotification(context: Context, state: ConnectionState): Notification =
        NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Listening for WashWatch alerts")
            .setContentText(
                when (state) {
                    ConnectionState.CONNECTED -> "Connected"
                    ConnectionState.OFFLINE -> "Offline — retrying"
                    ConnectionState.CONNECTING, ConnectionState.STOPPED -> "Connecting…"
                }
            )
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(openAppIntent(context))
            .build()

    fun updateServiceNotification(context: Context, state: ConnectionState) {
        if (!canPost(context)) return
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(SERVICE_NOTIFICATION_ID, serviceNotification(context, state))
    }

    /** Alerts delivered this late (after a reconnect or reboot) arrive silently; much older ones only go to the history. */
    private const val LATE_MILLIS = 15 * 60 * 1000L
    private const val STALE_MILLIS = 6 * 60 * 60 * 1000L

    /**
     * Posts [alert]. A "started" alert gets a live timer that counts up from the start and disappears after
     * [Cycles.MAX_RUNNING_SECONDS]; [lastCycle] (the machine's previous run) adds an estimated finish time.
     * A "done" alert replaces the timer and, given its [started] alert, sums up the whole cycle.
     */
    fun showAlert(context: Context, alert: Alert, lastCycle: LastCycle? = null, started: Alert? = null) {
        if (!canPost(context)) return
        val settings = Settings(context)
        val age = System.currentTimeMillis() - alert.time * 1000
        if (age > STALE_MILLIS) return
        val late = age > LATE_MILLIS
        val priority = when {
            alert.priority >= 4 -> NotificationCompat.PRIORITY_HIGH
            alert.priority <= 2 -> NotificationCompat.PRIORITY_LOW
            else -> NotificationCompat.PRIORITY_DEFAULT
        }
        val machine = alert.machine
        val kind = alert.kind
        val channel = when {
            machine != null && kind != null -> channelId(machine, kind, settings)
            alert.priority >= 4 -> CHANNEL_HIGH
            alert.priority <= 2 -> CHANNEL_LOW
            else -> CHANNEL_DEFAULT
        }
        val startedMillis = alert.time * 1000
        val timerLeftMillis = Cycles.MAX_RUNNING_SECONDS * 1000 - (System.currentTimeMillis() - startedMillis)
        val timed = kind == AlertKind.STARTED && machine != null && timerLeftMillis > 0
        val ranSeconds = Cycles.parseRanFor(alert.message)
        val text = when {
            timed && lastCycle != null -> {
                val doneAround = DateUtils.formatDateTime(context, startedMillis + lastCycle.seconds * 1000, DateUtils.FORMAT_SHOW_TIME)
                "Last cycle took ${Cycles.formatDuration(lastCycle.seconds)}, so expect it done around $doneAround."
            }
            kind == AlertKind.DONE && started != null && ranSeconds != null -> {
                val range = DateUtils.formatDateRange(
                    context, started.time * 1000, (started.time + ranSeconds) * 1000, DateUtils.FORMAT_SHOW_TIME,
                )
                "Ran ${Cycles.formatDuration(ranSeconds)}, $range."
            }
            else -> alert.message
        }
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setColor(ContextCompat.getColor(context, R.color.washwatch_blue))
            .setContentTitle(alert.displayTitle)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setWhen(startedMillis)
            .setShowWhen(true)
            .setPriority(priority)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
        if (late) builder.setSilent(true)
        if (timed) {
            // The header counts up from the start time; the notification removes itself when the timer would hit the cap.
            builder.setUsesChronometer(true).setTimeoutAfter(timerLeftMillis).setOnlyAlertOnce(true)
        }
        val notification = builder.build()
        if (kind == AlertKind.DONE && machine != null && !late && settings.doneAlertStyle.ringLikeAlarm) {
            // Repeat the sound and vibration until the alert is opened or swiped away.
            notification.flags = notification.flags or Notification.FLAG_INSISTENT
        }
        val id = if (timed) cycleNotificationId(machine!!) else alert.id.hashCode()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    /** Whether the notification for [alertId] is still in the shade. */
    fun isShowing(context: Context, alertId: String): Boolean =
        context.getSystemService(NotificationManager::class.java).activeNotifications.any { it.id == alertId.hashCode() }

    /** Removes the notification for [alertId], which also stops a repeating ("ring like an alarm") sound. */
    fun cancelAlert(context: Context, alertId: String) {
        NotificationManagerCompat.from(context).cancel(alertId.hashCode())
    }

    /** Clears the machine's running-cycle timer, e.g. once its "done" alert arrives. */
    fun cancelCycle(context: Context, machine: Machine) {
        NotificationManagerCompat.from(context).cancel(cycleNotificationId(machine))
    }
}
