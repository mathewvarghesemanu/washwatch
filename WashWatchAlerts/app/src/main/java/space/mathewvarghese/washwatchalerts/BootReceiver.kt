package space.mathewvarghese.washwatchalerts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Resumes listening after a reboot or an app update, if this phone is paired. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (Settings(context).subscription != null) AlertService.start(context)
    }
}
