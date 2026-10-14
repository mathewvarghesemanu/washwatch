package space.mathewvarghese.washwatchalerts

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings as SystemSettings
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import space.mathewvarghese.washwatchalerts.ui.theme.RunningGreen
import space.mathewvarghese.washwatchalerts.ui.theme.StoppedRed
import space.mathewvarghese.washwatchalerts.ui.theme.WashWatchTheme
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

private val ConnectingAmber = Color(0xFFE8A317)

class MainActivity : ComponentActivity() {
    private lateinit var settings: Settings
    private var subscription by mutableStateOf<Subscription?>(null)
    private var notificationsAllowed by mutableStateOf(true)
    private var batteryUnrestricted by mutableStateOf(true)

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let { pairFrom(it) }
    }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refreshPermissions() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        settings = Settings(this)
        Notifications.createChannels(this)
        subscription = settings.subscription
        AlertState.setHistory(settings.history)
        if (savedInstanceState == null) handleLink(intent)

        setContent {
            WashWatchTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AnimatedContent(
                        targetState = subscription,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "screen",
                    ) { current ->
                        if (current == null) PairScreen() else PairedScreen(current)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleLink(intent)
    }

    override fun onStart() {
        super.onStart()
        // Make sure the listener is running whenever the app is opened (e.g. after being force-stopped).
        if (subscription != null) AlertService.start(this)
    }

    override fun onResume() {
        super.onResume()
        // onResume, not onStart: the battery and notification prompts are dialogs that only pause this activity.
        refreshPermissions()
    }

    /** Opened from an ntfy:// link, e.g. after scanning the host's QR code with the stock camera app. */
    private fun handleLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (intent.action == Intent.ACTION_VIEW) pairFrom(data.toString())
    }

    private fun pairFrom(raw: String) {
        val parsed = Ntfy.parsePairing(raw)
        if (parsed == null) {
            Toast.makeText(this, "That isn't a WashWatch pairing code", Toast.LENGTH_LONG).show()
            return
        }
        if (parsed == subscription) {
            Toast.makeText(this, "Already paired with this WashWatch", Toast.LENGTH_SHORT).show()
            return
        }
        settings.pair(parsed)
        AlertState.setHistory(emptyList())
        subscription = parsed
        AlertService.restart(this)
        Toast.makeText(this, "Paired! Tap \"Send test\" on the WashWatch phone.", Toast.LENGTH_LONG).show()
        if (!notificationsAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun unpair() {
        settings.unpair()
        AlertState.setHistory(emptyList())
        subscription = null
        AlertService.stop(this)
    }

    private fun startScan() {
        scanLauncher.launch(
            ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt("Point at the QR code on the WashWatch phone")
                .setBeepEnabled(false)
                .setOrientationLocked(false)
        )
    }

    private fun refreshPermissions() {
        notificationsAllowed = Notifications.canPost(this)
        batteryUnrestricted = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
    }

    @SuppressLint("BatteryLife") // Sideloaded companion app; a push listener can't work under Doze otherwise.
    private fun requestBatteryExemption() {
        val intent = Intent(SystemSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
        runCatching { startActivity(intent) }
            .onFailure { startActivity(Intent(SystemSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
    }

    @Composable
    private fun PairScreen() {
        var showCodeDialog by remember { mutableStateOf(false) }
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
                modifier = Modifier
                    .size(96.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.LocalLaundryService,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(52.dp),
                )
            }
            Text("Pair with WashWatch", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Get a buzz on this phone when the laundry is done.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("1. On the WashWatch phone, open Phone notifications and tap \"Pair a phone\".")
                    Text("2. Tap Scan below and point this phone at the QR code.")
                    Text("3. Tap \"Send test\" on the WashWatch phone to check it works.")
                }
            }
            Button(onClick = ::startScan, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Scan QR code", fontWeight = FontWeight.SemiBold)
            }
            TextButton(onClick = { showCodeDialog = true }) { Text("Enter pairing code instead") }
        }
        if (showCodeDialog) PairingCodeDialog(onDismiss = { showCodeDialog = false })
    }

    @Composable
    private fun PairingCodeDialog(onDismiss: () -> Unit) {
        var code by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Enter pairing code") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "It's shown under the QR code on the WashWatch phone, e.g. washwatch-abc123…",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(value = code, onValueChange = { code = it.trim() }, singleLine = true, label = { Text("Pairing code") })
                }
            },
            confirmButton = {
                TextButton(enabled = code.isNotBlank(), onClick = { onDismiss(); pairFrom(code) }) { Text("Pair") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
    }

    @Composable
    private fun PairedScreen(current: Subscription) {
        val connection by AlertState.connection.collectAsState()
        val history by AlertState.history.collectAsState()
        var confirmUnpair by remember { mutableStateOf(false) }

        LazyColumn(
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            item { StatusCard(connection, current) }
            if (!notificationsAllowed) {
                item {
                    WarningCard(
                        icon = Icons.Filled.NotificationsOff,
                        text = "Notifications are off, so alerts can't reach you.",
                        action = "Allow",
                    ) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            startActivity(Intent(SystemSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(SystemSettings.EXTRA_APP_PACKAGE, packageName))
                        }
                    }
                }
            }
            if (!batteryUnrestricted) {
                item {
                    WarningCard(
                        icon = Icons.Filled.BatteryAlert,
                        text = "Battery saving may delay alerts while the screen is off.",
                        action = "Fix",
                        onClick = ::requestBatteryExemption,
                    )
                }
            }
            item {
                Text(
                    "Recent alerts",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (history.isEmpty()) {
                item {
                    Text(
                        "No alerts yet. Tap \"Send test\" on the WashWatch phone to check the connection.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(history, key = { it.id }) { AlertRow(it) }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    TextButton(onClick = ::startScan) { Text("Scan a new code") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { confirmUnpair = true }) { Text("Unpair") }
                }
            }
        }

        if (confirmUnpair) {
            AlertDialog(
                onDismissRequest = { confirmUnpair = false },
                title = { Text("Unpair?") },
                text = { Text("This phone will stop getting WashWatch alerts until you scan the QR code again.") },
                confirmButton = { TextButton(onClick = { confirmUnpair = false; unpair() }) { Text("Unpair") } },
                dismissButton = { TextButton(onClick = { confirmUnpair = false }) { Text("Cancel") } },
            )
        }
    }

    @Composable
    private fun StatusCard(connection: ConnectionState, current: Subscription) {
        val (label, target) = when (connection) {
            ConnectionState.CONNECTED -> "Listening for alerts" to RunningGreen
            ConnectionState.CONNECTING -> "Connecting…" to ConnectingAmber
            ConnectionState.OFFLINE -> "Offline — retrying" to StoppedRed
            ConnectionState.STOPPED -> "Starting…" to ConnectingAmber
        }
        val dotColor by animateColorAsState(target, label = "status")
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(Modifier.size(14.dp).background(dotColor, CircleShape))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Paired code: ${current.topic}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (current.server != Ntfy.DEFAULT_SERVER) {
                        Text(
                            "Server: ${current.server}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun WarningCard(icon: ImageVector, text: String, action: String, onClick: () -> Unit) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ),
        ) {
            Row(
                Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(icon, contentDescription = null)
                Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onClick) { Text(action) }
            }
        }
    }

    @Composable
    private fun AlertRow(alert: Alert) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (alert.priority >= 4) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
            ),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        alert.displayTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        relativeTime(alert.time * 1000),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (alert.message.isNotBlank()) {
                    Text(alert.message, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

    /** "Just now" for the first minute, which also covers the phone's clock being slightly behind ntfy's. */
    private fun relativeTime(millis: Long): String {
        val now = System.currentTimeMillis()
        if (now - millis < DateUtils.MINUTE_IN_MILLIS) return "Just now"
        return DateUtils.getRelativeTimeSpanString(millis, now, DateUtils.MINUTE_IN_MILLIS).toString()
    }
}
