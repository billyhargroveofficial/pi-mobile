package ru.billyhargrove.pimobile.features.updater

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.*

/** Original modal geometry; actions and progress are supplied by the state owner. */
@Composable
internal fun UpdateScreen(panel: UpdateSession.Panel, progress: Int, accept: () -> Unit, dismiss: () -> Unit) {
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (panel == UpdateSession.Panel.Progress) {
            Text("Updating Pi Mobile", fontSize = 22.sp)
            LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
            Text("Downloading · $progress%")
            TextButton(onClick = dismiss) { Text("Cancel") }
        } else {
            val update = (panel as? UpdateSession.Panel.Offer)?.update
            Text(if (update != null) "Pi Mobile ${update.version}" else "Allow app updates", fontSize = 22.sp)
            Text(if (update != null) "A new ${if (update.preview) "preview " else ""}release is available (${kotlin.math.round(update.size / 1024.0 / 1024).toInt()} MB).\n\nDownload, verify and open the Android installer? Your connection settings will be kept."
                else "Enable installation from Pi Mobile on the next screen, then return here. Android will still ask you to confirm the update.", fontSize = 15.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = dismiss) { Text("Later") }
                Button(onClick = accept) { Text(if (update != null) "Download update" else "Open settings") }
            }
        }
    }
}
