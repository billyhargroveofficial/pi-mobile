package ru.billyhargrove.pimobile.features.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.billyhargrove.pimobile.R

/** Existing thumbnail geometry and feedback; opening and authenticated loading belong to the adapter. */
@Composable
internal fun TranscriptImageScreen(bitmap: ImageBitmap?, index: Int, maxWidth: Dp, local: Boolean, error: String, open: () -> Unit) {
    if (bitmap != null) {
        val aspect = bitmap.width.toFloat() / bitmap.height
        val width = minOf(maxWidth, 280.dp * aspect)
        Image(bitmap, LocalContext.current.getString(R.string.cd_message_image, index + 1),
            modifier = Modifier.padding(top = 6.dp, bottom = 8.dp).size(width, width / aspect)
                .clip(RoundedCornerShape(16.dp)).clickable(onClick = open))
    } else Text(if (local) "Select attachment again to restore preview" else if (error.isEmpty()) "Loading image…" else "Image unavailable: $error",
        color = colorResource(R.color.text_secondary), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
}
