package space.mathewvarghese.washwatch

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import space.mathewvarghese.washwatch.alerts.AlertsActivity

val AppMode.label: String
    get() = when (this) {
        AppMode.MONITOR -> "Monitor mode"
        AppMode.ALERTS -> "Alerts mode"
    }

/** Where the phone is, which is how people think about the choice. */
val AppMode.where: String
    get() = when (this) {
        AppMode.MONITOR -> "On the machine"
        AppMode.ALERTS -> "With me"
    }

val AppMode.description: String
    get() = when (this) {
        AppMode.MONITOR -> "Feels the washer or dryer vibrate and sends an alert when it's done"
        AppMode.ALERTS -> "Buzzes when the laundry is done, wherever you are"
    }

val AppMode.icon: ImageVector
    get() = when (this) {
        AppMode.MONITOR -> Icons.Outlined.Vibration
        AppMode.ALERTS -> Icons.Outlined.NotificationsActive
    }

/**
 * Remembers [mode] and shows its screen, replacing this one. Each mode keeps running in the background
 * (a started monitor keeps monitoring, a paired phone keeps listening); switching only changes the screen.
 */
fun Activity.openMode(mode: AppMode) {
    Settings(this).mode = mode
    val target = when (mode) {
        AppMode.MONITOR -> MainActivity::class.java
        AppMode.ALERTS -> AlertsActivity::class.java
    }
    if (javaClass == target) return
    startActivity(Intent(this, target))
    finish()
    @Suppress("DEPRECATION") // overrideActivityTransition (API 34+) only covers this activity's own transitions.
    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
}

/** Top-bar title: the app name over the current mode, opening a menu to switch modes. */
@Composable
fun ModeSwitcher(current: AppMode, onSwitch: (AppMode) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClickLabel = "Switch mode") { open = true }
                .padding(start = 8.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
        ) {
            Column {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(current.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text(
                "This phone is…",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            AppMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = {
                        Column(Modifier.padding(vertical = 6.dp).width(220.dp)) {
                            Text(mode.where, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${mode.label} · ${mode.description}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    leadingIcon = { Icon(mode.icon, contentDescription = null) },
                    trailingIcon = if (mode == current) {
                        { Icon(Icons.Filled.Check, contentDescription = "Current mode", tint = MaterialTheme.colorScheme.primary) }
                    } else {
                        null
                    },
                    onClick = {
                        open = false
                        if (mode != current) onSwitch(mode)
                    },
                )
            }
            HorizontalDivider(Modifier.padding(top = 4.dp))
            Text(
                "Use one phone of each: one on the machine, one with you.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp).width(260.dp),
            )
        }
    }
}

/** First-run choice of mode. It can be changed later from the menu under the app name. */
@Composable
fun ModePicker(onPick: (AppMode) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(96.dp).background(scheme.primaryContainer, CircleShape),
        ) {
            Icon(Icons.Filled.LocalLaundryService, contentDescription = null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(52.dp))
        }
        Text("Where is this phone?", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "WashWatch uses two phones: a spare one on the machine, and yours. Install it on both and pick a mode on each.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = scheme.onSurfaceVariant,
        )
        AppMode.entries.forEach { mode ->
            OutlinedCard(onClick = { onPick(mode) }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(48.dp).background(scheme.secondaryContainer, CircleShape),
                    ) {
                        Icon(mode.icon, contentDescription = null, tint = scheme.onSecondaryContainer)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(mode.where, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(mode.label, style = MaterialTheme.typography.labelMedium, color = scheme.primary)
                        Text(mode.description, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = scheme.onSurfaceVariant)
                }
            }
        }
        Text(
            "You can switch any time from the menu under the app name.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = scheme.onSurfaceVariant,
        )
    }
}
