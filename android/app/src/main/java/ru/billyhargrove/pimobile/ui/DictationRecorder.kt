package ru.billyhargrove.pimobile.ui

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.media.*
import android.os.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import java.io.*
import java.util.Locale
import java.util.function.Consumer
import kotlin.math.sqrt

/** Foreground microphone capture to a bounded private PCM file. Returns draft text only. */
class DictationRecorder(context: Context, private val ready: Consumer<File>, private val error: Consumer<String>) {
    companion object { const val MAX_SECONDS = 600; const val SAMPLE_RATE = 16000; const val MAX_BYTES = SAMPLE_RATE * 2 * MAX_SECONDS }
    @Volatile private var recording = false
    @Volatile private var cancelled = false
    private var recorder: AudioRecord? = null
    private var audio: File? = null
    private var dialog: ComposeSheet? = null
    private val main = Handler(Looper.getMainLooper())
    private var seconds by mutableIntStateOf(0)
    private var levels by mutableStateOf(List(48) { 0f })
    private var finishing by mutableStateOf(false)
    init {
        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                throw SecurityException("Microphone permission is required for dictation")
            val dir = File(context.cacheDir, "dictation")
            if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Cannot create recording file")
            val file = File.createTempFile("recording-", ".pcm", dir); audio = file
            val minimum = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minimum <= 0) throw IOException("Microphone unavailable")
            val source = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(8192, minimum)); recorder = source
            if (source.state != AudioRecord.STATE_INITIALIZED) throw IOException("Microphone unavailable")
            source.startRecording(); recording = true
            dialog = object : ComposeSheet(context) { init { content {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text("Listening", fontSize = 20.sp)
                    Text(String.format(Locale.ENGLISH, "%02d:%02d", seconds / 60, seconds % 60), fontSize = 36.sp)
                    Waveform(levels, Modifier.fillMaxWidth().height(88.dp).testTag("${context.packageName}:id/voiceWaveform").semantics { testTagsAsResourceId = true })
                    Text("Up to 10 minutes · stays in your draft", fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = this@DictationRecorder::cancel, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text("Cancel") }
                        Button(onClick = { stop() }, enabled = !finishing, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text(if (finishing) "Finishing…" else "Finish") }
                    }
                }
            } } }.apply { setOnCancelListener { cancel() }; show() }
            Thread({ capture(source, file) }, "pi-mobile-microphone").start()
            main.postDelayed(::stop, MAX_SECONDS * 1000L)
        } catch (failure: Exception) {
            cancelled = true; recording = false
            try { recorder?.release() } catch (_: Exception) {}; recorder = null
            audio?.delete(); dialog?.dismiss()
            main.post { error.accept(failure.message ?: "Microphone unavailable") }
        }
    }
    private fun capture(source: AudioRecord, file: File) {
        val buffer = ByteArray(3200); var total = 0; var failure: String? = null
        try {
            BufferedOutputStream(FileOutputStream(file)).use { output ->
                while (recording && total < MAX_BYTES) {
                    val count = source.read(buffer, 0, minOf(buffer.size, MAX_BYTES - total))
                    if (count < 0) { if (recording) throw IOException("Could not capture microphone audio"); break }
                    if (count == 0) continue
                    output.write(buffer, 0, count); total += count
                    var sum = 0.0
                    for (i in 0 until count - 1 step 2) {
                        val sample = ((buffer[i].toInt() and 255) or (buffer[i + 1].toInt() shl 8)).toShort() / 32768.0
                        sum += sample * sample
                    }
                    val level = minOf(1.0, sqrt(sum / maxOf(1, count / 2)) * 4).toFloat(); val time = total / (SAMPLE_RATE * 2)
                    main.post { if (!cancelled) { levels = levels.drop(1) + level; seconds = time } }
                }
            }
        } catch (cause: Exception) { if (!cancelled) failure = cause.message ?: "Could not capture microphone audio" }
        finally { recording = false; try { source.stop() } catch (_: Exception) {}; source.release() }
        val problem = failure; val count = total
        main.post {
            recorder = null; main.removeCallbacksAndMessages(null); dialog?.dismiss()
            if (cancelled) file.delete()
            else if (problem != null || count < 3200) { file.delete(); error.accept(problem ?: "Recording is too short") }
            else ready.accept(file)
        }
    }
    private fun stop() { finishing = true; recording = false; try { recorder?.stop() } catch (_: Exception) {} }
    fun cancel() { cancelled = true; main.removeCallbacksAndMessages(null); stop(); dialog?.dismiss() }
}
