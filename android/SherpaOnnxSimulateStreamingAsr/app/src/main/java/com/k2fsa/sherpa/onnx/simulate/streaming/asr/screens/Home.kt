package com.k2fsa.sherpa.onnx.simulate.streaming.asr.screens

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.k2fsa.sherpa.onnx.simulate.streaming.asr.PreferencesHelper
import com.k2fsa.sherpa.onnx.simulate.streaming.asr.R
import com.k2fsa.sherpa.onnx.simulate.streaming.asr.SimulateStreamingAsr
import com.k2fsa.sherpa.onnx.simulate.streaming.asr.TAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private var audioRecord: AudioRecord? = null

private const val sampleRateInHz = 16000
private var samplesChannel = Channel<FloatArray>(capacity = Channel.UNLIMITED)

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val preferencesHelper = remember { PreferencesHelper(context) }

    val activity = LocalContext.current as Activity
    var isStarted by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    var isInitialized by remember { mutableStateOf(false) }
    var recognizedText by remember { mutableStateOf("") }

    // we change asrModelType in github actions
    val asrModelType = 15

    // Punctuation promotion state
    var punctuationState by remember { mutableStateOf("none") } // none, comma, period, paragraph
    var punctuationTimerJob by remember { mutableStateOf<Job?>(null) }

    // Load saved text when app starts
    LaunchedEffect(Unit) {
        if (asrModelType >= 9000) {
            // QNN info display removed for cleaner UI
        }

        withContext(Dispatchers.Default) {
            // Call your heavy initialization off the main thread
            SimulateStreamingAsr.initOfflineRecognizer(activity, asrModelType)
            SimulateStreamingAsr.initVad(activity.assets)
        }

        // Back on the Main thread: update UI state
        isInitialized = true
        
        // Load saved recognized text
        recognizedText = preferencesHelper.getRecognizedText()
    }

    // Save text whenever it changes
    LaunchedEffect(recognizedText) {
        if (isInitialized) {
            preferencesHelper.saveRecognizedText(recognizedText)
        }
    }

    // Character type checking functions
    fun isChineseChar(char: Char): Boolean {
        return char.code in 0x4E00..0x9FA5
    }

    fun isWesternChar(char: Char): Boolean {
        return char.code in 0x0020..0x007E
    }

    fun isFullWidthPunctuation(char: Char): Boolean {
        return char.code in 0x3000..0x303F || char.code in 0xFF00..0xFFEF
    }

    // Text normalization for internal spacing
    fun normalizeInternalSpacing(text: String): String {
        if (text.isEmpty()) return text
        
        val result = StringBuilder()
        for (i in text.indices) {
            result.append(text[i])
            
            if (i < text.length - 1) {
                val current = text[i]
                val next = text[i + 1]
                
                // Insert space between Chinese and Western characters
                if ((isChineseChar(current) && isWesternChar(next)) ||
                    (isWesternChar(current) && isChineseChar(next))) {
                    result.append(' ')
                }
            }
        }
        
        return result.toString()
    }

    // Smart text concatenation with boundary rules
    fun concatenateWithExistingText(existingText: String, newText: String): String {
        if (existingText.isEmpty()) return newText
        if (newText.isEmpty()) return existingText
        
        val tailChar = existingText.lastOrNull() ?: ' '
        val headChar = newText.firstOrNull() ?: ' '
        
        return when {
            // Rule 1: Full-width punctuation exemption
            isFullWidthPunctuation(tailChar) -> {
                existingText + newText
            }
            
            // Rule 2: Different character types - add space
            (isChineseChar(tailChar) && isWesternChar(headChar)) ||
            (isWesternChar(tailChar) && isChineseChar(headChar)) -> {
                existingText + " " + newText
            }
            
            // Rule 3: Default concatenation
            else -> {
                existingText + newText
            }
        }
    }

    // Punctuation promotion functions
    fun promotePunctuation() {
        val currentText = recognizedText
        when (punctuationState) {
            "none" -> {
                if (currentText.isNotEmpty() && !currentText.endsWith("，") &&
                    !currentText.endsWith("。") && !currentText.endsWith("\n")) {
                    recognizedText = currentText + "，"
                    punctuationState = "comma"
                }
            }
            "comma" -> {
                recognizedText = currentText.replaceLast("，", "。")
                punctuationState = "period"
            }
            "period" -> {
                recognizedText = currentText + "\n"
                punctuationState = "paragraph"
            }
        }
    }

    // ==========================================
    // 修订点：将 stopPunctuationTimer 移动到了 startPunctuationTimer 之前
    // ==========================================
    fun stopPunctuationTimer() {
        punctuationTimerJob?.cancel()
        punctuationTimerJob = null
    }

    fun startPunctuationTimer() {
        stopPunctuationTimer() // 现在这里可以正确引用了
        punctuationTimerJob = coroutineScope.launch {
            delay(500) // 500ms -> comma
            if (punctuationState == "none") {
                promotePunctuation()
            }
            
            delay(500) // additional 500ms (total 1000ms) -> period
            if (punctuationState == "comma") {
                promotePunctuation()
            }
            
            delay(500) // additional 500ms (total 1500ms) -> paragraph
            if (punctuationState == "period") {
                promotePunctuation()
            }
        }
    }

    val onRecordingButtonClick: () -> Unit = {
        isStarted = !isStarted
        if (isStarted) {
            if (ActivityCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.i(TAG, "Recording is not allowed")
            } else {
                // recording is allowed
                val audioSource = MediaRecorder.AudioSource.MIC
                val channelConfig = AudioFormat.CHANNEL_IN_MONO
                val audioFormat = AudioFormat.ENCODING_PCM_16BIT
                val numBytes =
                    AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat)
                audioRecord = AudioRecord(
                    audioSource,
                    sampleRateInHz,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    numBytes * 2 // a sample has two bytes as we are using 16-bit PCM
                )

                SimulateStreamingAsr.vad.reset()

                CoroutineScope(Dispatchers.IO).launch {
                    Log.i(TAG, "processing samples")
                    val interval = 0.1 // i.e., 100 ms
                    val bufferSize = (interval * sampleRateInHz).toInt() // in samples
                    val buffer = ShortArray(bufferSize)

                    audioRecord?.let { it ->
                        it.startRecording()

                        while (isStarted) {
                            val ret = audioRecord?.read(buffer, 0, buffer.size)
                            ret?.let { n ->
                                val samples = FloatArray(n) { buffer[it] / 32768.0f }
                                samplesChannel.send(samples)
                            }
                        }
                        val samples = FloatArray(0)
                        samplesChannel.send(samples)
                    }
                }

                CoroutineScope(Dispatchers.Default).launch {
                    var buffer = arrayListOf<Float>()
                    var offset = 0
                    val windowSize = 512
                    var isSpeechStarted = false
                    var startTime = System.currentTimeMillis()
                    var lastText = ""
                    var added = false
                    var speechStartOffset = 0

                    while (isStarted) {
                        for (s in samplesChannel) {
                            if (s.isEmpty()) {
                                break
                            }

                            buffer.addAll(s.toList())
                            while (offset + windowSize < buffer.size) {
                                SimulateStreamingAsr.vad.acceptWaveform(
                                    buffer.subList(
                                        offset,
                                        offset + windowSize
                                    ).toFloatArray()
                                )
                                offset += windowSize
                                if (!isSpeechStarted && SimulateStreamingAsr.vad.isSpeechDetected()) {
                                    isSpeechStarted = true
                                    // offset 0.4s
                                    speechStartOffset = offset - 6400
                                    if(speechStartOffset < 0) {
                                        speechStartOffset = 0
                                    }
                                    startTime = System.currentTimeMillis()
                                    stopPunctuationTimer()
                                }
                            }

                            val elapsed = System.currentTimeMillis() - startTime
                            if (isSpeechStarted && elapsed > 200) {
                                // Run ASR every 0.2 seconds == 200 milliseconds
                                // You can change it to some other value
                                val stream = SimulateStreamingAsr.recognizer.createStream()
                                stream.acceptWaveform(
                                    buffer.subList(speechStartOffset, offset).toFloatArray(),
                                    sampleRateInHz
                                )
                                SimulateStreamingAsr.recognizer.decode(stream)
                                val result = SimulateStreamingAsr.recognizer.getResult(stream)
                                stream.release()

                                lastText = result.text

                                if (lastText.isNotBlank()) {
                                    val cleanSegment = normalizeInternalSpacing(lastText)
                                    if (recognizedText.isEmpty()) {
                                        recognizedText = cleanSegment
                                    } else {
                                        val concatenated = concatenateWithExistingText(recognizedText, cleanSegment)
                                        if (concatenated != recognizedText) {
                                            recognizedText = concatenated
                                        }
                                    }
                                }

                                startTime = System.currentTimeMillis()
                            }

                            while (!SimulateStreamingAsr.vad.empty()) {
                                val stream = SimulateStreamingAsr.recognizer.createStream()
                                stream.acceptWaveform(
                                    SimulateStreamingAsr.vad.front().samples,
                                    sampleRateInHz
                                )
                                SimulateStreamingAsr.recognizer.decode(stream)
                                val result = SimulateStreamingAsr.recognizer.getResult(stream)
                                stream.release()

                                isSpeechStarted = false
                                SimulateStreamingAsr.vad.pop()

                                buffer = arrayListOf()
                                offset = 0
                                if (lastText.isNotBlank()) {
                                    val cleanSegment = normalizeInternalSpacing(result.text)
                                    if (recognizedText.isEmpty()) {
                                        recognizedText = cleanSegment
                                    } else {
                                        val concatenated = concatenateWithExistingText(recognizedText, cleanSegment)
                                        if (concatenated != recognizedText) {
                                            recognizedText = concatenated
                                        }
                                    }
                                    
                                    // Start punctuation timer after final result
                                    startPunctuationTimer()
                                }
                            }
                        }
                    }
                }
            }
        } else {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            stopPunctuationTimer()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(modifier = Modifier) {
            if (!isInitialized) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(text = "Initializing... Please wait")
                }
            }
            if (asrModelType >= 9000) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(text = "Qualcomm NPU (HTP backend with QNN)")
                }
            }

            HomeButtonRow(
                isStarted = isStarted,
                isInitialized = isInitialized,
                onRecordingButtonClick = onRecordingButtonClick,
                onCopyButtonClick = {
                    if (recognizedText.isNotEmpty()) {
                        clipboardManager.setText(AnnotatedString(recognizedText))
                        Toast.makeText(
                            context,
                            "Copied to clipboard",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            "Nothing to copy",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                onClearButtonClick = {
                    recognizedText = ""
                    punctuationState = "none"
                    stopPunctuationTimer()
                    preferencesHelper.clearRecognizedText() // Clear saved data
                }
            )

            if (recognizedText.isNotEmpty()) {
                Text(
                    text = recognizedText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .verticalScroll(scrollState)
                        .padding(16.dp)
                )
            }
        }
    }
}

@SuppressLint("UnrememberedMutableState")
@Composable
private fun HomeButtonRow(
    modifier: Modifier = Modifier,
    isStarted: Boolean,
    isInitialized: Boolean,
    onRecordingButtonClick: () -> Unit,
    onCopyButtonClick: () -> Unit,
    onClearButtonClick: () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        Button(
            onClick = onRecordingButtonClick,
            enabled = isInitialized,
        ) {
            Text(text = stringResource(if (isStarted) R.string.stop else R.string.start))
        }

        Spacer(modifier = Modifier.width(24.dp))

        Button(
            onClick = onCopyButtonClick,
            enabled = isInitialized,
        ) {
            Text(text = stringResource(id = R.string.copy))
        }

        Spacer(modifier = Modifier.width(24.dp))

        Button(
            onClick = onClearButtonClick,
            enabled = isInitialized,
        ) {
            Text(text = stringResource(id = R.string.clear))
        }
    }
}

// Extension function to replace last occurrence
private fun String.replaceLast(oldValue: String, newValue: String, ignoreCase: Boolean = false): String {
    val lastIndex = this.lastIndexOf(oldValue, ignoreCase = ignoreCase)
    return if (lastIndex >= 0) {
        this.substring(0, lastIndex) + newValue + this.substring(lastIndex + oldValue.length)
    } else {
        this
    }
}
