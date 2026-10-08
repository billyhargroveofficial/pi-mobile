package ru.billyhargrove.pimobile.features.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*

/** Existing document geometry/scroll/actions; native Markdown is an injected presentation leaf. */
@Composable
internal fun DocumentScreen(path: String, source: String, maxHeight: Dp, packageName: String,
    close: () -> Unit, markdown: @Composable (String, Modifier) -> Unit) {
    val prefix = "$packageName:id/"
    Column(Modifier.fillMaxWidth().heightIn(max = maxHeight)
        .navigationBarsPadding().padding(20.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(path, Modifier.weight(1f).testTag(prefix + "documentPath").semantics { testTagsAsResourceId = true }, maxLines = 2, fontSize = 16.sp)
            TextButton(onClick = close, modifier = Modifier.testTag(prefix + "documentClose").semantics { testTagsAsResourceId = true }) { Text("Close") }
        }
        Column(Modifier.weight(1f, false).verticalScroll(rememberScrollState())
            .testTag(prefix + "documentScroll").semantics { testTagsAsResourceId = true }) {
            markdown(source, Modifier.fillMaxWidth().padding(vertical = 16.dp))
        }
    }
}
