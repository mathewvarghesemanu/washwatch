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
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Notification channels and builders. Each machine gets its own "done", "started" and "hasn't started"
 * channels (grouped under the machine in Android's settings) so they can have different sounds. Alerts with no
 * machine or kind (e.g. the host's test message) fall back to generic channels picked by ntfy priority (1–5).
 */
object Notifications {
    const val SERVICE_NOTIFICATION_ID = 1
    private const val CHANNEL_SERVICE = "connection"
    private const val CHANNEL_HIGH = "alerts_high"
    private const val CHANNEL_DEFAULT = "alerts_default"
    private const val CHANNEL_LOW = "alerts_low"

    private fun channelId(machine: Machine, kind: AlertKind) = "${machine.key}_${kind.channelSuffix}"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannelGroups(Machine.entries.map { NotificationChannelGroup(it.key, it.label) })
        manager.createNotificationChannels(
            Machine.entries.flatMap { machine ->
                listOf(
                    NotificationChannel(channelId(machine, AlertKind.DONE), "${machine.label} done", NotificationManager.IMPORTANCE_HIGH).apply {
                        description = "When the ${machine.label.lowercase()} finishes its cycle."
                        group = machine.key
                        enableVibration(true)
                    },
                    NotificationChannel(channelId(machine, AlertKind.STARTED), "${machine.label} started", NotificationManager.IMPORTANCE_LOW).apply {
                        description = "Quiet heads-up when the ${machine.label.lowercase()} starts running."
                        group = machine.key
                    },
                    NotificationChannel(channelId(machine, AlertKind.WARNING), "${machine.label} hasn't started", NotificationManager.IMPORTANCE_HIGH).apply {
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

    fun showAlert(context: Context, alert: Alert) {
        if (!canPost(context)) return
        val priority = when {
            alert.priority >= 4 -> NotificationCompat.PRIORITY_HIGH
            alert.priority <= 2 -> NotificationCompat.PRIORITY_LOW
            else -> NotificationCompat.PRIORITY_DEFAULT
        }
        val machine = alert.machine
        val kind = alert.kind
        val channel = when {
            machine != null && kind != null -> channelId(machine, kind)
            alert.priority >= 4 -> CHANNEL_HIGH
            alert.priority <= 2 -> CHANNEL_LOW
            else -> CHANNEL_DEFAULT
        }
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setColor(ContextCompat.getColor(context, R.color.washwatch_blue))
            .setContentTitle(alert.displayTitle)
            .setContentText(alert.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.message))
            .setWhen(alert.time * 1000)
            .setShowWhen(true)
            .setPriority(priority)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(context).notify(alert.id.hashCode(), notification)
    }
}
