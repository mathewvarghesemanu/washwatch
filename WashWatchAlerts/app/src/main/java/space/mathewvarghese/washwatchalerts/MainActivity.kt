package space.mathewvarghese.washwatchalerts

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings as SystemSettings
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.LocalLaundryService
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
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
    private var showSettings by mutableStateOf(false)
    private var showSoundPicker by mutableStateOf(false)
    private var showOlder by mutableStateOf(false)
    private var testPlaying by mutableStateOf(false)
    private var darkMode by mutableStateOf(true)
    private var doneStyle by mutableStateOf(DoneAlertStyle(null, vibrate = true, ringLikeAlarm = false))
    /** Bumped every minute and whenever the phone's clock or timezone changes, so alert times re-render. */
    private var clockNow by mutableLongStateOf(System.currentTimeMillis())

    private val clockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            clockNow = System.currentTimeMillis()
        }
    }

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
        doneStyle = settings.doneAlertStyle
        darkMode = settings.darkMode
        AlertState.setHistory(settings.history)
        if (savedInstanceState == null) handleLink(intent)

        setContent {
            WashWatchTheme(darkTheme = darkMode) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AnimatedContent(
                        targetState = subscription,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "screen",
                    ) { current ->
                        when {
                            current == null -> PairScreen()
                            showSettings && showSoundPicker -> SoundPickerScreen(
                                selected = doneStyle.sound,
                                ringLikeAlarm = doneStyle.ringLikeAlarm,
                                onPick = { updateDoneStyle(doneStyle.copy(sound = it)) },
                                onClose = { showSoundPicker = false },
                            )
                            showSettings -> SettingsScreen(current)
                            else -> PairedScreen()
                        }
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
        clockNow = System.currentTimeMillis()
        val clockFilter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(this, clockReceiver, clockFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(clockReceiver)
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
        showSettings = false
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

    private fun updateDoneStyle(style: DoneAlertStyle) {
        settings.setDoneAlertStyle(style)
        doneStyle = settings.doneAlertStyle
        darkMode = settings.darkMode
        Notifications.createChannels(this)
    }

    private fun soundName(uri: Uri?): String {
        if (uri == null) return "Silent (vibrate only)"
        return runCatching { RingtoneManager.getRingtone(this, uri)?.getTitle(this) }.getOrNull() ?: "Custom sound"
    }

    /** Posts a sample "done" alert through the real channel so the chosen sound and vibration can be checked. */
    private fun sendTestDoneAlert() {
        if (!Notifications.canPost(this)) {
            Toast.makeText(this, "Allow notifications first", Toast.LENGTH_SHORT).show()
            return
        }
        Notifications.cancelAlert(this, TEST_ALERT_ID) // Restart the sound if it's already playing.
        Notifications.showAlert(
            this,
            Alert(
                id = TEST_ALERT_ID,
                time = System.currentTimeMillis() / 1000,
                title = "Washing Machine done!",
                message = "This is how a finished cycle will sound.",
                priority = 4,
                tags = listOf(Machine.WASHER.key, AlertKind.DONE.tag, "basket"),
            ),
        )
        testPlaying = true
    }

    private fun stopTestDoneAlert() {
        Notifications.cancelAlert(this, TEST_ALERT_ID)
        testPlaying = false
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
                    Text("1. On the WashWatch phone, open Settings → Notifications and tap \"Pair a phone\".")
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

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun PairedScreen() {
        val connection by AlertState.connection.collectAsState()
        val history by AlertState.history.collectAsState()
        var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
        val running = Cycles.running(history, nowMillis / 1000)
        val nowSeconds = maxOf(nowMillis, clockNow) / 1000
        val timeline = Cycles.timeline(history, nowSeconds)
        val older = timeline.filter { nowSeconds - it.time >= Cycles.RECENT_SECONDS }
        val shown = if (showOlder) timeline else timeline - older.toSet()
        // Tick once a second while a cycle is being timed; the card drops off by itself at the 3-hour cap.
        LaunchedEffect(running.isNotEmpty()) {
            while (running.isNotEmpty()) {
                nowMillis = System.currentTimeMillis()
                delay(1000)
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    actions = {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                        }
                    },
                )
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = screenPadding(padding),
            ) {
                item { ConnectionChip(connection, Modifier.padding(bottom = 16.dp)) }
                if (!notificationsAllowed) {
                    item {
                        NoticeCard(
                            icon = Icons.Outlined.NotificationsOff,
                            text = "Notifications are off, so alerts can't reach you.",
                            action = "Allow",
                            modifier = Modifier.padding(bottom = 12.dp),
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
                        NoticeCard(
                            icon = Icons.Outlined.BatteryAlert,
                            text = "Battery saving may delay alerts while the screen is off.",
                            action = "Fix",
                            modifier = Modifier.padding(bottom = 12.dp),
                            onClick = ::requestBatteryExemption,
                        )
                    }
                }
                items(running, key = { "running_${it.machine.key}" }) {
                    RunningCard(it, nowMillis, Modifier.padding(bottom = 12.dp))
                }
                if (history.isEmpty()) {
                    item { EmptyHistory() }
                }
                shown.groupBy { dayLabel(it.time * 1000) }.forEach { (day, entries) ->
                    item(key = "day_$day") { SectionHeader(day, Modifier.padding(top = 12.dp)) }
                    itemsIndexed(entries, key = { _, entry -> entry.key }) { index, entry ->
                        HistoryRow(entry, groupShape(index, entries.size), Modifier.padding(bottom = 2.dp))
                    }
                }
                if (older.isNotEmpty()) {
                    item(key = "older") {
                        Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                            TextButton(onClick = { showOlder = !showOlder }) {
                                Text(if (showOlder) "Hide older" else "Show ${older.size} older")
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ConnectionChip(connection: ConnectionState, modifier: Modifier = Modifier) {
        val (label, target) = when (connection) {
            ConnectionState.CONNECTED -> "Listening for alerts" to RunningGreen
            ConnectionState.CONNECTING -> "Connecting…" to ConnectingAmber
            ConnectionState.OFFLINE -> "Offline — retrying" to StoppedRed
            ConnectionState.STOPPED -> "Starting…" to ConnectingAmber
        }
        val dotColor by animateColorAsState(target, label = "status")
        Surface(modifier = modifier, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(8.dp).background(dotColor, CircleShape))
                Text(label, style = MaterialTheme.typography.labelLarge)
            }
        }
    }

    @Composable
    private fun NoticeCard(
        icon: ImageVector,
        text: String,
        action: String,
        modifier: Modifier = Modifier,
        onClick: () -> Unit,
    ) {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ),
        ) {
            Row(
                Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
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
    private fun RunningCard(cycle: RunningCycle, nowMillis: Long, modifier: Modifier = Modifier) {
        val elapsed = nowMillis / 1000 - cycle.startedAt
        val last = cycle.last
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(cycle.machine.icon, contentDescription = null)
                    Text(
                        "${cycle.machine.label} running",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "since ${clockTime(cycle.startedAt * 1000)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                }
                Text(
                    Cycles.formatElapsed(elapsed),
                    style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Medium,
                )
                if (last != null) {
                    LinearProgressIndicator(
                        progress = { (elapsed.toFloat() / last.seconds).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                    )
                }
                Text(
                    when {
                        last == null -> "No previous ${cycle.machine.label.lowercase()} cycle yet, so no estimate this time."
                        elapsed <= last.seconds ->
                            "Done around ${clockTime((cycle.startedAt + last.seconds) * 1000)} · last cycle ${Cycles.formatDuration(last.seconds)}"
                        else -> "Running longer than last cycle (${Cycles.formatDuration(last.seconds)})"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    @Composable
    private fun EmptyHistory() {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Outlined.NotificationsNone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp),
            )
            Text("No alerts yet", style = MaterialTheme.typography.titleMedium)
            Text(
                "Tap \"Send test\" on the WashWatch phone to check the connection.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }

    @Composable
    private fun HistoryRow(entry: HistoryEntry, shape: Shape, modifier: Modifier = Modifier) {
        when (entry) {
            is HistoryEntry.Single -> AlertRow(entry.alert, shape, modifier)
            is HistoryEntry.Cycle -> CycleRow(entry, shape, modifier)
        }
    }

    /** A whole cycle on one row: machine, start–finish times and how long it ran. */
    @Composable
    private fun CycleRow(cycle: HistoryEntry.Cycle, shape: Shape, modifier: Modifier = Modifier) {
        val started = cycle.started
        val done = cycle.done
        val ran = cycle.ranSeconds
        val summary = when {
            done == null -> "Started ${clockTime(started!!.time * 1000)} · finish not heard"
            started != null -> DateUtils.formatDateRange(
                this,
                started.time * 1000,
                (if (ran != null) started.time + ran else done.time) * 1000,
                DateUtils.FORMAT_SHOW_TIME,
            )
            else -> "Finished ${clockTime(done.time * 1000)}"
        }
        val finished = done != null
        Surface(modifier = modifier.fillMaxWidth(), shape = shape, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = {
                    Box(
                        Modifier.size(40.dp).background(
                            if (finished) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape,
                        ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            cycle.machine.icon,
                            contentDescription = null,
                            tint = if (finished) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
                headlineContent = { Text(cycle.machine.label) },
                supportingContent = { Text(summary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                trailingContent = if (finished && ran != null) {
                    { Text(Cycles.formatDuration(ran), style = MaterialTheme.typography.labelLarge) }
                } else {
                    null
                },
            )
        }
    }

    @Composable
    private fun AlertRow(alert: Alert, shape: Shape, modifier: Modifier = Modifier) {
        val (icon, container, content) = when (alert.kind) {
            AlertKind.DONE -> Triple(Icons.Filled.Check, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
            AlertKind.STARTED -> Triple(Icons.Filled.PlayArrow, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
            AlertKind.WARNING -> Triple(Icons.Filled.PriorityHigh, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
            null -> Triple(Icons.Outlined.Notifications, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(modifier = modifier.fillMaxWidth(), shape = shape, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = {
                    Box(
                        Modifier.size(40.dp).background(container, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
                    }
                },
                headlineContent = { Text(alert.title.ifBlank { "WashWatch" }) },
                supportingContent = alert.message.takeIf { it.isNotBlank() }?.let {
                    { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                },
                trailingContent = {
                    Text(alertTime(alert.time * 1000, clockNow), style = MaterialTheme.typography.labelMedium)
                },
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SettingsScreen(current: Subscription) {
        BackHandler { showSettings = false }
        var confirmUnpair by remember { mutableStateOf(false) }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Settings") },
                    navigationIcon = {
                        IconButton(onClick = { showSettings = false }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(screenPadding(padding)),
            ) {
                SectionHeader("When laundry is done")
                SettingsGroup {
                    SettingItem(
                        icon = Icons.Outlined.MusicNote,
                        title = "Sound",
                        subtitle = soundName(doneStyle.sound),
                        onClick = { showSoundPicker = true },
                    )
                    SettingItem(
                        icon = Icons.Outlined.Vibration,
                        title = "Vibrate",
                        checked = doneStyle.vibrate,
                        onClick = { updateDoneStyle(doneStyle.copy(vibrate = !doneStyle.vibrate)) },
                    )
                    SettingItem(
                        icon = Icons.Outlined.Alarm,
                        title = "Ring like an alarm",
                        subtitle = "Repeats at alarm volume until dismissed",
                        checked = doneStyle.ringLikeAlarm,
                        onClick = { updateDoneStyle(doneStyle.copy(ringLikeAlarm = !doneStyle.ringLikeAlarm)) },
                    )
                    TestAlertButtons()
                }

                SectionHeader("Pairing", Modifier.padding(top = 24.dp))
                SettingsGroup {
                    SettingItem(
                        icon = Icons.Outlined.Link,
                        title = "Paired code",
                        subtitle = if (current.server == Ntfy.DEFAULT_SERVER) current.topic else "${current.topic} · ${current.server}",
                    )
                    SettingItem(icon = Icons.Outlined.QrCodeScanner, title = "Scan a new code", onClick = ::startScan)
                    SettingItem(
                        icon = Icons.Outlined.LinkOff,
                        title = "Unpair",
                        tint = MaterialTheme.colorScheme.error,
                        onClick = { confirmUnpair = true },
                    )
                }

                SectionHeader("More", Modifier.padding(top = 24.dp))
                SettingsGroup {
                    SettingItem(
                        icon = Icons.Outlined.DarkMode,
                        title = "Dark mode",
                        checked = darkMode,
                        onClick = {
                            darkMode = !darkMode
                            settings.darkMode = darkMode
                        },
                    )
                    SettingItem(
                        icon = Icons.Outlined.Tune,
                        title = "Android notification settings",
                        subtitle = "Do Not Disturb, lock screen and more",
                        onClick = {
                            startActivity(Intent(SystemSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(SystemSettings.EXTRA_APP_PACKAGE, packageName))
                        },
                    )
                }
            }
        }

        if (confirmUnpair) {
            AlertDialog(
                onDismissRequest = { confirmUnpair = false },
                icon = { Icon(Icons.Outlined.LinkOff, contentDescription = null) },
                title = { Text("Unpair?") },
                text = { Text("This phone will stop getting WashWatch alerts until you scan the QR code again.") },
                confirmButton = { TextButton(onClick = { confirmUnpair = false; unpair() }) { Text("Unpair") } },
                dismissButton = { TextButton(onClick = { confirmUnpair = false }) { Text("Cancel") } },
            )
        }
    }

    /** Play and Stop side by side; Stop stays enabled while the test alert is in the shade. */
    @Composable
    private fun TestAlertButtons() {
        // Notice when the test alert is swiped away or tapped from the shade instead of stopped here.
        LaunchedEffect(testPlaying) {
            while (testPlaying) {
                delay(1000)
                if (!Notifications.isShowing(this@MainActivity, TEST_ALERT_ID)) testPlaying = false
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(onClick = ::sendTestDoneAlert, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Play test")
            }
            OutlinedButton(onClick = ::stopTestDoneAlert, enabled = testPlaying, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Stop")
            }
        }
    }

    /**
     * A settings row. With [checked] it shows a switch and the whole row toggles it;
     * without [onClick] it is plain information.
     */
    @Composable
    private fun SettingItem(
        icon: ImageVector,
        title: String,
        subtitle: String? = null,
        checked: Boolean? = null,
        tint: Color = Color.Unspecified,
        onClick: (() -> Unit)? = null,
    ) {
        val rowModifier = when {
            checked != null && onClick != null -> Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = { onClick() })
            onClick != null -> Modifier.clickable(onClick = onClick)
            else -> Modifier
        }
        val color = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onSurface else tint
        ListItem(
            modifier = rowModifier,
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                Icon(icon, contentDescription = null, tint = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else tint)
            },
            headlineContent = { Text(title, color = color) },
            supportingContent = subtitle?.let { { Text(it) } },
            trailingContent = checked?.let { { Switch(checked = it, onCheckedChange = null) } },
        )
    }

    /**
     * "Just now" or "5 min. ago" within the hour, then the wall-clock time in the phone's timezone and 12/24-hour
     * setting (the day header above gives the date). ntfy timestamps are UTC epoch seconds.
     */
    private fun alertTime(millis: Long, now: Long): String = when {
        now - millis < DateUtils.MINUTE_IN_MILLIS -> "Just now" // Also covers the phone's clock being slightly behind ntfy's.
        now - millis < DateUtils.HOUR_IN_MILLIS ->
            DateUtils.getRelativeTimeSpanString(millis, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()
        else -> clockTime(millis)
    }

    /** "Today", "Yesterday", then e.g. "Mon, Oct 12". */
    private fun dayLabel(millis: Long): String = when {
        DateUtils.isToday(millis) -> "Today"
        DateUtils.isToday(millis + DateUtils.DAY_IN_MILLIS) -> "Yesterday"
        else -> DateUtils.formatDateTime(
            this,
            millis,
            DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH,
        )
    }

    private fun clockTime(millis: Long): String = DateUtils.formatDateTime(this, millis, DateUtils.FORMAT_SHOW_TIME)
}

/** Fixed ID so the test alert can be found and stopped again. */
private const val TEST_ALERT_ID = "settings-test-alert"

/** Side gutters plus the scaffold's insets. */
fun screenPadding(insets: PaddingValues) = PaddingValues(
    start = 16.dp,
    end = 16.dp,
    top = insets.calculateTopPadding() + 4.dp,
    bottom = insets.calculateBottomPadding() + 24.dp,
)

/** Rows of one list sit 2dp apart; the outer corners are round and the inner ones nearly square. */
fun groupShape(index: Int, count: Int): Shape {
    val outer = 20.dp
    val inner = 4.dp
    return RoundedCornerShape(
        topStart = if (index == 0) outer else inner,
        topEnd = if (index == 0) outer else inner,
        bottomStart = if (index == count - 1) outer else inner,
        bottomEnd = if (index == count - 1) outer else inner,
    )
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, bottom = 8.dp),
    )
}

@Composable
fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(content = content)
    }
}

val Machine.icon: ImageVector
    get() = when (this) {
        Machine.WASHER -> Icons.Outlined.LocalLaundryService
        Machine.DRYER -> Icons.Outlined.Air
    }
