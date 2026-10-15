package space.mathewvarghese.washwatchalerts

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One choosable sound; a null [uri] means silent. */
data class SoundOption(val title: String, val uri: Uri?)

private data class SoundSection(val title: String, val options: List<SoundOption>)

/** Sounds installed on the phone, read straight from RingtoneManager so it works the same on every launcher and ROM. */
private fun installedSounds(context: Context, type: Int): List<SoundOption> {
    val manager = RingtoneManager(context).apply { setType(type) }
    val cursor = runCatching { manager.cursor }.getOrNull() ?: return emptyList()
    val sounds = mutableListOf<SoundOption>()
    while (cursor.moveToNext()) {
        val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX) ?: continue
        sounds += SoundOption(title, manager.getRingtoneUri(cursor.position))
    }
    return sounds.distinctBy { it.uri }.sortedBy { it.title.lowercase() }
}

/**
 * Full-screen list of sounds for the "done" alert. Tapping one selects it and plays a preview;
 * [onPick] saves it immediately, and back closes the screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundPickerScreen(selected: Uri?, ringLikeAlarm: Boolean, onPick: (Uri?) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    var preview by remember { mutableStateOf<Ringtone?>(null) }
    DisposableEffect(Unit) { onDispose { preview?.stop() } }
    BackHandler(onBack = onClose)

    val sections by produceState(emptyList<SoundSection>()) {
        value = withContext(Dispatchers.IO) {
            listOf(
                SoundSection(
                    "Basic",
                    listOf(
                        SoundOption("Silent (vibrate only)", null),
                        SoundOption("Phone's notification sound", android.provider.Settings.System.DEFAULT_NOTIFICATION_URI),
                        SoundOption("Phone's alarm sound", android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI),
                        SoundOption("Phone's ringtone", android.provider.Settings.System.DEFAULT_RINGTONE_URI),
                    ),
                ),
                SoundSection("Chimes", installedSounds(context, RingtoneManager.TYPE_NOTIFICATION)),
                SoundSection("Alarms", installedSounds(context, RingtoneManager.TYPE_ALARM)),
                SoundSection("Ringtones", installedSounds(context, RingtoneManager.TYPE_RINGTONE)),
            ).filter { it.options.isNotEmpty() }
        }
    }

    fun choose(option: SoundOption) {
        onPick(option.uri)
        preview?.stop()
        preview = option.uri?.let { uri ->
            runCatching {
                RingtoneManager.getRingtone(context, uri)?.apply {
                    audioAttributes = AudioAttributes.Builder()
                        .setUsage(if (ringLikeAlarm) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    play()
                }
            }.getOrNull()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Done sound") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = screenPadding(padding)) {
            sections.forEachIndexed { sectionIndex, section ->
                item(key = "header_${section.title}") {
                    SectionHeader(section.title, Modifier.padding(top = if (sectionIndex == 0) 0.dp else 24.dp))
                }
                itemsIndexed(section.options, key = { _, it -> "${section.title}_${it.uri}_${it.title}" }) { index, option ->
                    val isSelected = option.uri == selected
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                        shape = groupShape(index, section.options.size),
                        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        ListItem(
                            modifier = Modifier.selectable(selected = isSelected, role = Role.RadioButton) { choose(option) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            leadingContent = { RadioButton(selected = isSelected, onClick = null) },
                            headlineContent = { Text(option.title) },
                        )
                    }
                }
            }
        }
    }
}
