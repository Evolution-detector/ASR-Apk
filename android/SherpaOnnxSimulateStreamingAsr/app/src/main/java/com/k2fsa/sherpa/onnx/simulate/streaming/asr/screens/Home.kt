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
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.unit.sp
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

    fun stopPunctuationTimer() {
        punctuationTimerJob?.cancel()
        punctuationTimerJob = null
    }

    fun startPunctuationTimer() {
        stopPunctuationTimer()
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
                    
                    // stableText 用于保存“已经说完的句子”
                    // 防止 Offline Model 模拟流式输出时的重复问题
                    var stableText = recognizedText
                    var speechStartOffset = 0

                    while (isStarted) {
                        for (s in samplesChannel) {
                            if (s.isEmpty()) {
                                break
                            }

                            // 1. 累积音频并送入 VAD
                            buffer.addAll(s.toList())
                            while (offset + windowSize < buffer.size) {
                                SimulateStreamingAsr.vad.acceptWaveform(
                                    buffer.subList(offset, offset + windowSize).toFloatArray()
                                )
                                offset += windowSize
                            }

                            // 2. 检测 VAD 状态
                            val isCurrentSpeechDetected = SimulateStreamingAsr.vad.isSpeechDetected()
                            
                            // === 状态A: 开始说话 ===
                            if (isCurrentSpeechDetected && !isSpeechStarted) {
                                isSpeechStarted = true
                                // 锁定当前屏幕上已有的稳定文本
                                stableText = recognizedText 
                                
                                // 计算本次说话在 buffer 中的起始位置
                                speechStartOffset = offset - 6400
                                if (speechStartOffset < 0) speechStartOffset = 0
                                startTime = System.currentTimeMillis()
                                stopPunctuationTimer()
                            } 
                            
                            // === 状态B: 停止说话 (检测到静音) ===
                            if (!isCurrentSpeechDetected && isSpeechStarted) {
                                isSpeechStarted = false
                                
                                // 重点：对 buffer 里剩余的内容做最后一次解码，防止丢掉句尾
                                if (offset > speechStartOffset) {
                                     val stream = SimulateStreamingAsr.recognizer.createStream()
                                     stream.acceptWaveform(
                                         buffer.subList(speechStartOffset, offset).toFloatArray(),
                                         sampleRateInHz
                                     )
                                     SimulateStreamingAsr.recognizer.decode(stream)
                                     val result = SimulateStreamingAsr.recognizer.getResult(stream)
                                     stream.release()
                                     
                                     if (result.text.isNotBlank()) {
                                         val cleanSegment = normalizeInternalSpacing(result.text)
                                         // 最终合并
                                         recognizedText = concatenateWithExistingText(stableText, cleanSegment)
                                     }
                                }

                                // 将完整的一句话晋升为 stableText
                                stableText = recognizedText 
                                
                                // 清理环境
                                buffer = arrayListOf()
                                offset = 0
                                SimulateStreamingAsr.vad.reset()
                                
                                // 启动标点计时器
                                startPunctuationTimer()
                            }

                            // === 状态C: 正在说话 (实时预览) ===
                            val elapsed = System.currentTimeMillis() - startTime
                            if (isSpeechStarted && elapsed > 200) {
                                // 每 200ms 解码当前片段
                                val stream = SimulateStreamingAsr.recognizer.createStream()
                                stream.acceptWaveform(
                                    buffer.subList(speechStartOffset, offset).toFloatArray(),
                                    sampleRateInHz
                                )
                                SimulateStreamingAsr.recognizer.decode(stream)
                                val result = SimulateStreamingAsr.recognizer.getResult(stream)
                                stream.release()

                                val lastText = result.text

                                if (lastText.isNotBlank()) {
                                    val cleanSegment = normalizeInternalSpacing(lastText)
                                    // 重点：始终是 稳定文本 + 当前完整片段，避免重复
                                    recognizedText = concatenateWithExistingText(stableText, cleanSegment)
                                }
                                startTime = System.currentTimeMillis()
                            }

                            // 清理 VAD 队列
                            while (!SimulateStreamingAsr.vad.empty()) {
                                SimulateStreamingAsr.vad.pop()
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
                    // 使用资源引用，实现双语
                    Text(text = stringResource(R.string.Loading))
                }
            }
            if (asrModelType >= 9000) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    // 使用资源引用，实现双语
                    Text(text = stringResource(R.string.qualcomm_npu_backend))
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
                            context.getString(R.string.copied_to_clipboard), // 使用资源引用
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.nothing_to_copy), // 使用资源引用
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

            // 支持文本选择，字体大小 20sp
            if (recognizedText.isNotEmpty()) {
                SelectionContainer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(16.dp)
                ) {
                    Text(
                        text = recognizedText,
                        fontSize = 20.sp,
                        lineHeight = 26.sp,
                        modifier = Modifier.verticalScroll(scrollState)
                    )
                }
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
