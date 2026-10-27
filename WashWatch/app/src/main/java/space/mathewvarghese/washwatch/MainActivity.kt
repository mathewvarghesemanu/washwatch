package space.mathewvarghese.washwatch

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.LocalLaundryService
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import space.mathewvarghese.washwatch.ui.theme.RunningGreen
import space.mathewvarghese.washwatch.ui.theme.StoppedRed
import space.mathewvarghese.washwatch.ui.theme.WashWatchTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val CALIBRATION_COUNTDOWN_SECONDS = 3
private const val CALIBRATION_MEASURE_MILLIS = 1000L

/** Shrinks slightly while pressed and springs back on release. */
@Composable
private fun Modifier.pressScale(interaction: MutableInteractionSource, pressedScale: Float = 0.96f): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) pressedScale else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pressScale",
    )
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

private val Appliance.icon: ImageVector
    get() = when (this) {
        Appliance.WASHER -> Icons.Outlined.LocalLaundryService
        Appliance.DRYER -> Icons.Outlined.Air
    }

/** Phases of the full-screen "measure the off baseline" flow. */
private sealed interface Calibration {
    data class Countdown(val secondsLeft: Int) : Calibration
    data object Measuring : Calibration
    data class Done(val threshold: Float) : Calibration
}

class MainActivity : ComponentActivity() {

    private lateinit var settings: Settings
    /** Drives the theme; read from settings in onCreate and flipped by the Appearance switch. */
    private val darkMode = mutableStateOf(true)
    /** Null on a fresh install until the user picks a mode. */
    private val mode = mutableStateOf<AppMode?>(null)

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startMonitorService()
            } else {
                Toast.makeText(this, "Notification permission is required to run the monitor.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        mode.value = settings.mode
        // The launcher always opens this activity; send Alerts-mode phones straight on to their screen.
        if (mode.value == AppMode.ALERTS && intent.action == Intent.ACTION_MAIN && savedInstanceState == null) {
            openMode(AppMode.ALERTS)
            return
        }
        darkMode.value = settings.darkMode
        enableEdgeToEdge()
        setContent {
            WashWatchTheme(darkTheme = darkMode.value) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    if (mode.value == null) {
                        ModePicker { picked ->
                            if (picked == AppMode.MONITOR) mode.value = picked
                            openMode(picked)
                        }
                    } else {
                        WashWatchApp()
                    }
                }
                // Saveable so rotating the phone doesn't replay the intro.
                var showIntro by rememberSaveable { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    delay(INTRO_VISIBLE_MILLIS)
                    showIntro = false
                }
                AnimatedVisibility(visible = showIntro, enter = fadeIn(tween(0)), exit = fadeOut(tween(200))) {
                    IntroSplash()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Reopen in whichever mode was used last, including when this screen was reached from a notification.
        if (mode.value != null) settings.mode = AppMode.MONITOR
    }

    private fun toggleService() {
        if (VibrationMonitorService.isRunning.value) {
            stopService(Intent(this, VibrationMonitorService::class.java))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startMonitorService()
        }
    }

    private fun startMonitorService() {
        ContextCompat.startForegroundService(this, Intent(this, VibrationMonitorService::class.java))
    }

    /**
     * Records linear-acceleration magnitudes for [measureMillis] after a [countdownSeconds] delay and
     * returns max × [Settings.BASELINE_MULTIPLIER]. The listener runs during the countdown too, so the
     * gravity filter has settled by the time measuring starts.
     */
    private suspend fun measureBaseline(
        countdownSeconds: Int,
        measureMillis: Long,
        onPhase: (Calibration) -> Unit,
    ): Float {
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val filter = LinearAccelerationFilter()
        var recording = false
        var max = 0f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val magnitude = filter.magnitude(event.values)
                if (recording && magnitude > max) max = magnitude
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        try {
            for (s in countdownSeconds downTo 1) {
                onPhase(Calibration.Countdown(s))
                delay(1000)
            }
            onPhase(Calibration.Measuring)
            recording = true
            delay(measureMillis)
            recording = false
        } finally {
            sensorManager.unregisterListener(listener)
        }
        return max
    }

    @Composable
    private fun WashWatchApp() {
        var appliance by remember { mutableStateOf(settings.appliance) }
        var calibration by remember { mutableStateOf<Calibration?>(null) }
        // Bumped after calibration so threshold-dependent UI re-reads preferences.
        var thresholdVersion by remember { mutableIntStateOf(0) }
        var showSettings by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        BackHandler(enabled = showSettings) { showSettings = false }

        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = showSettings,
                transitionSpec = {
                    val forward = targetState
                    (slideInHorizontally(tween(320)) { if (forward) it else -it / 3 } + fadeIn(tween(320))) togetherWith
                        (slideOutHorizontally(tween(320)) { if (forward) -it / 3 else it } + fadeOut(tween(220)))
                },
                label = "navigation",
            ) { settingsOpen ->
                if (!settingsOpen) {
                    HomeScreen(
                        appliance = appliance,
                        thresholdVersion = thresholdVersion,
                        onSelectAppliance = {
                            appliance = it
                            settings.appliance = it
                        },
                        onOpenSettings = { showSettings = true },
                    )
                } else {
                    SettingsScreen(
                        appliance = appliance,
                        thresholdVersion = thresholdVersion,
                        onBack = { showSettings = false },
                        onThresholdChanged = { thresholdVersion++ },
                        onCalibrate = { selected ->
                            scope.launch {
                                val baseline = measureBaseline(CALIBRATION_COUNTDOWN_SECONDS, CALIBRATION_MEASURE_MILLIS) {
                                    calibration = it
                                }
                                settings.setBaseline(selected, baseline)
                                thresholdVersion++
                                calibration = Calibration.Done(settings.threshold(selected) ?: Settings.DEFAULT_THRESHOLD)
                                delay(1800)
                                calibration = null
                            }
                        },
                    )
                }
            }

            // Keep the last phase around so the overlay can fade out after calibration clears.
            var lastPhase by remember { mutableStateOf<Calibration?>(null) }
            if (calibration != null) lastPhase = calibration
            AnimatedVisibility(visible = calibration != null, enter = fadeIn(tween(250)), exit = fadeOut(tween(350))) {
                lastPhase?.let { CalibrationOverlay(it) }
            }
        }
    }

    @Composable
    private fun SectionHeader(text: String) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, top = 24.dp, bottom = 8.dp),
        )
    }

    // ---------------------------------------------------------------- Home

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun HomeScreen(
        appliance: Appliance?,
        thresholdVersion: Int,
        onSelectAppliance: (Appliance) -> Unit,
        onOpenSettings: () -> Unit,
    ) {
        val isRunning by VibrationMonitorService.isRunning.collectAsState()
        var gearTurns by remember { mutableIntStateOf(0) }
        val gearRotation by animateFloatAsState(gearTurns * 120f, tween(450), label = "gear")

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { ModeSwitcher(AppMode.MONITOR) { openMode(it) } },
                    actions = {
                        IconButton(onClick = { gearTurns += 1; onOpenSettings() }) {
                            Icon(Icons.Outlined.Settings, contentDescription = "Settings", modifier = Modifier.rotate(gearRotation))
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(8.dp))
                ServiceButton(isRunning = isRunning, enabled = appliance != null, onClick = { toggleService() })
                Spacer(Modifier.height(8.dp))
                StatusCard(appliance = appliance, isRunning = isRunning, thresholdVersion = thresholdVersion)

                SectionHeader("Appliance")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Appliance.entries.forEach { option ->
                        ApplianceCard(
                            appliance = option,
                            selected = appliance == option,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelectAppliance(option) },
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    @Composable
    private fun ServiceButton(isRunning: Boolean, enabled: Boolean, onClick: () -> Unit) {
        val color by animateColorAsState(if (isRunning) RunningGreen else StoppedRed, tween(450), label = "serviceColor")
        val ringAlpha by animateFloatAsState(if (isRunning) 1f else 0f, tween(500), label = "ringAlpha")
        val haptics = LocalHapticFeedback.current
        val interaction = remember { MutableInteractionSource() }
        val transition = rememberInfiniteTransition(label = "pulse")
        val pulse by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
            label = "pulseProgress",
        )
        val spin by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
            label = "spin",
        )

        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(232.dp)) {
            if (ringAlpha > 0f) {
                // Two staggered rings expanding outward like ripples.
                listOf(pulse, (pulse + 0.5f) % 1f).forEach { p ->
                    Box(
                        Modifier
                            .size(168.dp)
                            .scale(1f + p * 0.35f)
                            .alpha((1f - p) * 0.35f * ringAlpha)
                            .background(color, CircleShape)
                    )
                }
                CircularProgressIndicator(
                    progress = { 0.22f },
                    modifier = Modifier.size(188.dp).alpha(ringAlpha).rotate(spin),
                    color = color,
                    strokeWidth = 4.dp,
                    trackColor = Color.Transparent,
                    strokeCap = StrokeCap.Round,
                )
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(168.dp)
                    .pressScale(interaction, pressedScale = 0.92f)
                    .alpha(if (enabled || isRunning) 1f else 0.38f)
                    .shadow(if (enabled || isRunning) 8.dp else 0.dp, CircleShape)
                    .clip(CircleShape)
                    .background(color)
                    .clickable(
                        interactionSource = interaction,
                        indication = ripple(color = Color.White),
                        enabled = enabled || isRunning,
                    ) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onClick()
                    },
            ) {
                AnimatedContent(
                    targetState = isRunning,
                    transitionSpec = {
                        (fadeIn(tween(250)) + scaleIn(initialScale = 0.7f)) togetherWith
                            (fadeOut(tween(150)) + scaleOut(targetScale = 1.2f))
                    },
                    label = "serviceLabel",
                ) { running ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            if (running) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(40.dp),
                        )
                        Text(
                            if (running) "Running" else "Stopped",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (running) "Tap to stop" else "Tap to start",
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun StatusCard(appliance: Appliance?, isRunning: Boolean, thresholdVersion: Int) {
        val status by VibrationMonitorService.status.collectAsState()
        val rms by VibrationMonitorService.rms.collectAsState()
        val threshold = remember(appliance, thresholdVersion) {
            appliance?.let { settings.threshold(it) } ?: Settings.DEFAULT_THRESHOLD
        }
        val scheme = MaterialTheme.colorScheme

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp).animateContentSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(40.dp).background(scheme.secondaryContainer, CircleShape),
                    ) {
                        Icon(Icons.Outlined.Sensors, contentDescription = null, tint = scheme.onSecondaryContainer)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Status", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                        AnimatedContent(
                            targetState = when {
                                isRunning -> status
                                appliance == null -> "Choose an appliance below to get started"
                                else -> "Ready. Tap the button to start monitoring"
                            },
                            transitionSpec = {
                                (fadeIn(tween(250)) + slideInVertically { it / 2 }) togetherWith
                                    (fadeOut(tween(150)) + slideOutVertically { -it / 2 })
                            },
                            label = "status",
                        ) { text ->
                            Text(text, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                AnimatedVisibility(visible = isRunning, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    Column(Modifier.padding(top = 16.dp)) {
                        // Bar is full at 2× the threshold, so the midpoint marks "counts as running".
                        val level by animateFloatAsState((rms / (threshold * 2)).coerceIn(0f, 1f), tween(300), label = "level")
                        LinearProgressIndicator(
                            progress = { level },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                            color = if (rms > threshold) RunningGreen else scheme.primary,
                            trackColor = scheme.surfaceContainerHighest,
                            strokeCap = StrokeCap.Round,
                            drawStopIndicator = {},
                        )
                        Spacer(Modifier.height(6.dp))
                        Row {
                            Text(
                                String.format("Vibration %.3f m/s²", rms),
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                String.format("Threshold %.3f", threshold),
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ApplianceCard(appliance: Appliance, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
        val scheme = MaterialTheme.colorScheme
        val haptics = LocalHapticFeedback.current
        val interaction = remember { MutableInteractionSource() }
        val container by animateColorAsState(if (selected) scheme.primaryContainer else scheme.surface, tween(300), label = "cardColor")
        val content by animateColorAsState(if (selected) scheme.onPrimaryContainer else scheme.onSurface, tween(300), label = "contentColor")
        val borderColor by animateColorAsState(if (selected) scheme.primary else scheme.outlineVariant, tween(300), label = "borderColor")
        val borderWidth by animateDpAsState(if (selected) 2.dp else 1.dp, tween(300), label = "borderWidth")

        OutlinedCard(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
            interactionSource = interaction,
            modifier = modifier.height(128.dp).pressScale(interaction),
            border = BorderStroke(borderWidth, borderColor),
            colors = CardDefaults.outlinedCardColors(containerColor = container, contentColor = content),
        ) {
            Box(Modifier.fillMaxSize()) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = selected,
                    enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                    exit = scaleOut() + fadeOut(),
                    modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = "Selected", tint = scheme.primary, modifier = Modifier.size(20.dp))
                }
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(appliance.icon, contentDescription = null, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        appliance.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }

    // ------------------------------------------------------------ Settings

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SettingsScreen(
        appliance: Appliance?,
        thresholdVersion: Int,
        onBack: () -> Unit,
        onThresholdChanged: () -> Unit,
        onCalibrate: (Appliance) -> Unit,
    ) {
        val snackbar = remember { SnackbarHostState() }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Settings") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbar, modifier = Modifier.navigationBarsPadding()) },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                if (appliance != null) {
                    SectionHeader("${appliance.label} detection")
                    ApplianceSettingsCard(
                        appliance,
                        thresholdVersion,
                        onThresholdChanged = onThresholdChanged,
                        onCalibrate = { onCalibrate(appliance) },
                    )
                } else {
                    SectionHeader("Detection")
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        ListItem(
                            headlineContent = { Text("No appliance selected") },
                            supportingContent = { Text("Choose a washing machine or dryer on the home screen to configure detection.") },
                            leadingContent = { Icon(Icons.Outlined.Tune, contentDescription = null) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }

                SectionHeader("Notifications")
                PairPhoneCard(snackbar)

                SectionHeader("Appearance")
                AppearanceCard()
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    @Composable
    private fun ApplianceSettingsCard(
        appliance: Appliance,
        thresholdVersion: Int,
        onThresholdChanged: () -> Unit,
        onCalibrate: () -> Unit,
    ) {
        var confirmText by remember(appliance) { mutableStateOf(settings.confirmSeconds(appliance).toString()) }
        val baseline = remember(appliance, thresholdVersion) { settings.baseline(appliance) }
        var multiplier by remember(appliance) { mutableFloatStateOf(settings.multiplier(appliance)) }
        val threshold = baseline?.let { (it * multiplier).coerceAtLeast(Settings.MIN_THRESHOLD) }
        val transparent = ListItemDefaults.colors(containerColor = Color.Transparent)
        val seconds = confirmText.toLongOrNull() ?: 0

        ElevatedCard(Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text("Confirmation time") },
                supportingContent = {
                    Text(
                        if (seconds > 0) {
                            "Counts as finished after ${formatDuration(seconds)} with zero vibration. " +
                                "Any vibration restarts the timer."
                        } else {
                            "How long the machine must stay completely still to count as finished."
                        }
                    )
                },
                leadingContent = { Icon(Icons.Outlined.Timer, contentDescription = null) },
                colors = transparent,
            )
            OutlinedTextField(
                value = confirmText,
                onValueChange = { text ->
                    confirmText = text.filter { it.isDigit() }.take(5)
                    confirmText.toIntOrNull()?.takeIf { it > 0 }?.let { settings.setConfirmSeconds(appliance, it) }
                },
                label = { Text("Seconds") },
                suffix = { Text("s") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = seconds <= 0,
                supportingText = if (seconds <= 0) ({ Text("Enter a value greater than 0") }) else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 56.dp, end = 16.dp, bottom = 8.dp),
            )

            HorizontalDivider(Modifier.padding(horizontal = 16.dp))

            ListItem(
                headlineContent = { Text("Off baseline") },
                supportingContent = {
                    Text(
                        if (threshold != null) {
                            String.format("Vibration above %.3f m/s² counts as running.", threshold)
                        } else {
                            "Not calibrated. Using default threshold of %.2f m/s².".format(Settings.DEFAULT_THRESHOLD)
                        }
                    )
                },
                leadingContent = { Icon(Icons.Outlined.Tune, contentDescription = null) },
                colors = transparent,
            )
            val calibrateInteraction = remember { MutableInteractionSource() }
            Button(
                onClick = onCalibrate,
                interactionSource = calibrateInteraction,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 56.dp, end = 16.dp, bottom = 16.dp)
                    .height(48.dp)
                    .pressScale(calibrateInteraction),
            ) {
                Icon(Icons.Outlined.Sensors, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Calibrate (machine off)")
            }

            HorizontalDivider(Modifier.padding(horizontal = 16.dp))

            ListItem(
                headlineContent = { Text("Sensitivity multiplier") },
                supportingContent = {
                    Text(
                        if (baseline != null) {
                            String.format(
                                "Running means %.1f× the calibrated %.3f m/s². Lower is more sensitive; raise it if footsteps or a nearby machine trigger it.",
                                multiplier,
                                baseline,
                            )
                        } else {
                            "Applies once calibrated. Lower is more sensitive; raise it if footsteps or a nearby machine trigger it."
                        }
                    )
                },
                leadingContent = { Icon(Icons.Outlined.Speed, contentDescription = null) },
                trailingContent = {
                    Text(
                        String.format("%.1f×", multiplier),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                colors = transparent,
            )
            Slider(
                value = multiplier,
                onValueChange = { multiplier = (it * 2).roundToInt() / 2f }, // 0.5× steps
                onValueChangeFinished = {
                    settings.setMultiplier(appliance, multiplier)
                    onThresholdChanged()
                },
                valueRange = Settings.MIN_MULTIPLIER..Settings.MAX_MULTIPLIER,
                steps = ((Settings.MAX_MULTIPLIER - Settings.MIN_MULTIPLIER) * 2).toInt() - 1,
                modifier = Modifier.padding(start = 56.dp, end = 16.dp),
            )
            if (multiplier != Settings.DEFAULT_MULTIPLIER) {
                TextButton(
                    onClick = {
                        multiplier = Settings.DEFAULT_MULTIPLIER
                        settings.setMultiplier(appliance, multiplier)
                        onThresholdChanged()
                    },
                    modifier = Modifier.padding(start = 44.dp, bottom = 8.dp),
                ) { Text("Reset to %.0f×".format(Settings.DEFAULT_MULTIPLIER)) }
            } else {
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    /** Shows a QR code that subscribes another phone's ntfy app to this monitor's private topic. */
    @Composable
    private fun PairPhoneCard(snackbar: SnackbarHostState) {
        var topic by remember { mutableStateOf(settings.ntfyTopic) }
        val scope = rememberCoroutineScope()
        val newTopic = { topic = Ntfy.randomTopic().also { settings.ntfyTopic = it } }
        val transparent = ListItemDefaults.colors(containerColor = Color.Transparent)
        val scheme = MaterialTheme.colorScheme

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.animateContentSize()) {
                val current = topic
                ListItem(
                    headlineContent = { Text("Phone alerts") },
                    supportingContent = {
                        Text(
                            if (current == null) {
                                "Get start and done alerts on your phone: install WashWatch on it and pick Alerts mode."
                            } else {
                                "Paired. Alerts go to every phone subscribed to this code."
                            }
                        )
                    },
                    leadingContent = { Icon(Icons.Outlined.NotificationsActive, contentDescription = null) },
                    colors = transparent,
                )
                if (current == null) {
                    val pairInteraction = remember { MutableInteractionSource() }
                    FilledTonalButton(
                        onClick = newTopic,
                        interactionSource = pairInteraction,
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 56.dp, end = 16.dp, bottom = 16.dp)
                            .height(48.dp)
                            .pressScale(pairInteraction),
                    ) {
                        Icon(Icons.Outlined.QrCode2, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("Pair a phone")
                    }
                } else {
                    Column(
                        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val qr = remember(current) { Ntfy.qrBitmap(Ntfy.subscribeLink(current)).asImageBitmap() }
                        Image(
                            bitmap = qr,
                            contentDescription = "QR code to pair a phone",
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .size(220.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White)
                                .padding(10.dp),
                        )
                        Text(
                            "1. On your phone, open WashWatch in Alerts mode.\n" +
                                "2. Tap Scan QR code and point it at this code.\n" +
                                "3. Tap Send test to confirm it arrives.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Surface(color = scheme.surfaceContainerHighest, shape = RoundedCornerShape(12.dp)) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Text("Pairing code", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                                SelectionContainer {
                                    Text(current, style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace)
                                }
                                Text(
                                    "Can't scan? Tap \"Enter pairing code instead\" in WashWatch, or subscribe to this topic in the ntfy app.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurfaceVariant,
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                                onClick = {
                                    scope.launch {
                                        val ok = Ntfy.publish(current, "WashWatch paired", "Test notification — you're all set.", tags = listOf("tada"))
                                        snackbar.showSnackbar(if (ok) "Test sent. Check the other phone" else "Couldn't reach ntfy.sh")
                                    }
                                },
                            ) {
                                Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                Text("Send test")
                            }
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                                onClick = {
                                    val share = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "To get WashWatch alerts: open WashWatch in Alerts mode, tap \"Enter pairing code instead\" " +
                                                "and enter $current\nOr open ${Ntfy.subscribeLink(current)}",
                                        )
                                    }
                                    startActivity(Intent.createChooser(share, "Share pairing link"))
                                },
                            ) {
                                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                Text("Share")
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = newTopic, contentPadding = ButtonDefaults.TextButtonWithIconContentPadding) {
                                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                Text("New code")
                            }
                            Spacer(Modifier.weight(1f))
                            TextButton(
                                onClick = { topic = null; settings.ntfyTopic = null },
                                colors = ButtonDefaults.textButtonColors(contentColor = scheme.error),
                            ) { Text("Unpair") }
                        }
                        Text(
                            "New code stops alerts to phones paired with the old one.",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun AppearanceCard() {
        val dark = darkMode.value
        val toggle = { on: Boolean ->
            darkMode.value = on
            settings.darkMode = on
        }
        ElevatedCard(Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text("Dark mode") },
                supportingContent = { Text(if (dark) "On. Turn off for the light theme." else "Off. Using the light theme.") },
                leadingContent = { Icon(Icons.Outlined.DarkMode, contentDescription = null) },
                trailingContent = { Switch(checked = dark, onCheckedChange = toggle) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { toggle(!dark) },
            )
        }
    }

    // --------------------------------------------------------- Calibration

    @Composable
    private fun CalibrationOverlay(phase: Calibration) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF20B0F14))
                // Swallow touches so nothing underneath can be tapped mid-measurement.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            AnimatedContent(
                targetState = phase,
                transitionSpec = {
                    (fadeIn(tween(200)) + scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy), initialScale = 0.6f)) togetherWith
                        (fadeOut(tween(150)) + scaleOut(targetScale = 1.3f))
                },
                contentAlignment = Alignment.Center,
                label = "calibrationPhase",
            ) { current ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.padding(32.dp),
                ) {
                    when (current) {
                        is Calibration.Countdown -> {
                            Text("Calibrating in", color = Color.White, style = MaterialTheme.typography.titleLarge)
                            // Ring drains over each second, so every number visibly ticks down.
                            val ring = remember { Animatable(1f) }
                            LaunchedEffect(Unit) { ring.animateTo(0f, tween(1000, easing = LinearEasing)) }
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
                                CircularProgressIndicator(
                                    progress = { ring.value },
                                    modifier = Modifier.fillMaxSize(),
                                    color = Color.White,
                                    trackColor = Color.White.copy(alpha = 0.15f),
                                    strokeWidth = 8.dp,
                                    strokeCap = StrokeCap.Round,
                                    gapSize = 0.dp,
                                )
                                Text(
                                    "${current.secondsLeft}",
                                    color = Color.White,
                                    style = MaterialTheme.typography.displayLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Text(
                                "Keep the machine off and don't touch the phone.",
                                color = Color.White.copy(alpha = 0.75f),
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                            )
                        }
                        Calibration.Measuring -> {
                            Text("Measuring", color = Color.White, style = MaterialTheme.typography.titleLarge)
                            CircularProgressIndicator(
                                color = Color.White,
                                trackColor = Color.White.copy(alpha = 0.15f),
                                strokeWidth = 8.dp,
                                strokeCap = StrokeCap.Round,
                                modifier = Modifier.size(200.dp),
                            )
                            Text("Hold still…", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyLarge)
                        }
                        is Calibration.Done -> {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = RunningGreen,
                                modifier = Modifier.size(120.dp).background(Color.White, CircleShape),
                            )
                            Text(
                                "Calibration complete",
                                color = Color.White,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                String.format("Vibration above %.3f m/s² will count as running.", current.threshold),
                                color = Color.White.copy(alpha = 0.75f),
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}
