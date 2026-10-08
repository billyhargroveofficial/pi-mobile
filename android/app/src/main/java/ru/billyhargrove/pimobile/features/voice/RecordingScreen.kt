package ru.billyhargrove.pimobile.features.voice

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import java.util.Locale
import ru.billyhargrove.pimobile.ui.Waveform

/** Existing recording geometry and actions; no audio, timers, file or transport work. */
@Composable
internal fun RecordingScreen(seconds: Int, levels: List<Float>, finishing: Boolean, packageName: String,
    finish: () -> Unit, cancel: () -> Unit) {
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Listening", fontSize = 20.sp)
        Text(String.format(Locale.ENGLISH, "%02d:%02d", seconds / 60, seconds % 60), fontSize = 36.sp)
        Waveform(levels, Modifier.fillMaxWidth().height(88.dp).testTag("$packageName:id/voiceWaveform").semantics { testTagsAsResourceId = true })
        Text("Up to 10 minutes · stays in your draft", fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = cancel, modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("$packageName:id/recordingCancel").semantics { testTagsAsResourceId = true }) { Text("Cancel") }
            Button(onClick = finish, enabled = !finishing, modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("$packageName:id/recordingFinish").semantics { testTagsAsResourceId = true }) { Text(if (finishing) "Finishing…" else "Finish") }
        }
    }
}
