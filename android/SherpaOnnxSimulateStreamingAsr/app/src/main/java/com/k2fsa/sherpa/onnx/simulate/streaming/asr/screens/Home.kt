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
  
// 标点晋升状态枚举  
enum class PunctuationState {  
    NONE,           // 无标点  
    COMMA,          // 逗号  
    PERIOD,         // 句号  
    PARAGRAPH       // 段落  
}  
  
@Composable  
fun HomeScreen() {  
    val context = LocalContext.current  
    val clipboardManager = LocalClipboardManager.current  
  
    val activity = LocalContext.current as Activity  
    var isStarted by remember { mutableStateOf(false) }  
    var recognizedText by remember { mutableStateOf("") }  
    val scrollState = rememberScrollState()  
    val coroutineScope = rememberCoroutineScope()  
  
    var isInitialized by remember { mutableStateOf(false) }  
  
    // 标点晋升相关状态  
    var punctuationState by remember { mutableStateOf(PunctuationState.NONE) }  
    var punctuationTimerJob by remember { mutableStateOf<Job?>(null) }  
    var lastSegmentText by remember { mutableStateOf("") }  
  
    // we change asrModelType in github actions  
    val asrModelType = 15  
  
    LaunchedEffect(Unit) {  
        if (asrModelType >= 9000) {  
            recognizedText = "Using QNN for Qualcomm NPU (HTP backend)\nIt takes about 10s for the first run to start\nLater runs require less than 1 second\n"  
        }  
  
        withContext(Dispatchers.Default) {  
            // Call your heavy initialization off the main thread  
            SimulateStreamingAsr.initOfflineRecognizer(activity, asrModelType)  
            SimulateStreamingAsr.initVad(activity.assets)  
        }  
  
        // Back on the Main thread: update UI state  
        isInitialized = true  
        recognizedText = ""  
    }  
  
    // 标点晋升函数  
    fun promotePunctuation() {  
        when (punctuationState) {  
            PunctuationState.NONE -> {  
                punctuationState = PunctuationState.COMMA  
                // 添加逗号  
                if (recognizedText.isNotEmpty() && !recognizedText.endsWith("，")) {  
                    recognizedText += "，"  
                }  
            }  
            PunctuationState.COMMA -> {  
                punctuationState = PunctuationState.PERIOD  
                // 替换逗号为句号  
                if (recognizedText.endsWith("，")) {  
                    recognizedText = recognizedText.dropLast(1) + "。"  
                }  
            }  
            PunctuationState.PERIOD -> {  
                punctuationState = PunctuationState.PARAGRAPH  
                // 添加换行符  
                if (recognizedText.isNotEmpty()) {  
                    recognizedText += "\n"  
                }  
            }  
            PunctuationState.PARAGRAPH -> {  
                // 已经是段落，不再晋升  
            }  
        }  
          
        // 更新UI滚动  
        coroutineScope.launch {  
            scrollState.scrollTo(scrollState.maxValue)  
        }  
    }  
  
    // 开始标点晋升计时  
    fun startPunctuationTimer() {  
        // 取消之前的计时器  
        punctuationTimerJob?.cancel()  
          
        punctuationTimerJob = CoroutineScope(Dispatchers.Default).launch {  
            // 阶段二：500ms后晋升为逗号  
            delay(500)  
            if (punctuationState == PunctuationState.NONE) {  
                withContext(Dispatchers.Main) {  
                    promotePunctuation()  
                }  
            }  
              
            // 阶段三：1000ms后晋升为句号  
            delay(500) // 总计1000ms  
            if (punctuationState == PunctuationState.COMMA) {  
                withContext(Dispatchers.Main) {  
                    promotePunctuation()  
                }  
            }  
              
            // 阶段四：1500ms后晋升为段落  
            delay(500) // 总计1500ms  
            if (punctuationState == PunctuationState.PERIOD) {  
                withContext(Dispatchers.Main) {  
                    promotePunctuation()  
                }  
            }  
        }  
    }  
  
    // 停止标点晋升计时（打断机制）  
    fun stopPunctuationTimer() {  
        punctuationTimerJob?.cancel()  
        punctuationTimerJob = null  
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
                  
                // 重置标点状态  
                punctuationState = PunctuationState.NONE  
                lastSegmentText = ""  
  
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
                    var speechStartOffset = 0  
                    var currentSegmentText = ""  // 当前语音段的文本  
  
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
                                  
                                // 检测到语音开始（打断机制）  
                                if (!isSpeechStarted && SimulateStreamingAsr.vad.isSpeechDetected()) {  
                                    isSpeechStarted = true  
                                    speechStartOffset = offset - 6400  
                                    if(speechStartOffset < 0) {  
                                        speechStartOffset = 0  
                                    }  
                                    startTime = System.currentTimeMillis()  
                                      
                                    // 停止标点晋升计时  
                                    stopPunctuationTimer()  
                                      
                                    // 新语音段开始时，清空当前段文本  
                                    currentSegmentText = ""  
                                }  
                            }  
  
                            val elapsed = System.currentTimeMillis() - startTime  
                            if (isSpeechStarted && elapsed > 200) {  
                                // Run ASR every 0.2 seconds == 200 milliseconds  
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
                                    currentSegmentText = lastText  
                                      
                                    // 实时显示当前段的结果（不带标点）  
                                    if (recognizedText.isEmpty()) {  
                                        recognizedText = currentSegmentText  
                                    } else {  
                                        // 移除最后的标点，显示纯文本  
                                        val baseText = if (recognizedText.endsWith("，") ||   
                                                         recognizedText.endsWith("。") ||  
                                                         recognizedText.endsWith("\n")) {  
                                            recognizedText.dropLast(1)  
                                        } else {  
                                            recognizedText  
                                        }  
                                          
                                        // 按段落分割，替换最后一段  
                                        val paragraphs = baseText.split("\n").toMutableList()  
                                        if (paragraphs.isNotEmpty()) {  
                                            val lastParagraph = paragraphs.last()  
                                            val segments = lastParagraph.split("，").toMutableList()  
                                            if (segments.isNotEmpty()) {  
                                                segments[segments.size - 1] = currentSegmentText  
                                                paragraphs[paragraphs.size - 1] = segments.joinToString("，")  
                                                recognizedText = paragraphs.joinToString("\n")  
                                            }  
                                        }  
                                    }  
  
                                    coroutineScope.launch {  
                                        scrollState.scrollTo(scrollState.maxValue)  
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
  
                                // VAD结束时，确认最终结果并开始标点晋升  
                                if (result.text.isNotBlank()) {  
                                    lastSegmentText = result.text  
                                      
                                    // 更新显示文本  
                                    if (recognizedText.isEmpty()) {  
                                        recognizedText = lastSegmentText  
                                    } else {  
                                        // 移除最后的标点，添加最终结果  
                                        val baseText = if (recognizedText.endsWith("，") ||   
                                                         recognizedText.endsWith("。") ||  
                                                         recognizedText.endsWith("\n")) {  
                                            recognizedText.dropLast(1)  
                                        } else {  
                                            recognizedText  
                                        }  
                                          
                                        val paragraphs = baseText.split("\n").toMutableList()  
                                        if (paragraphs.isNotEmpty()) {  
                                            val lastParagraph = paragraphs.last()  
                                            val segments = lastParagraph.split("，").toMutableList()  
                                            if (segments.isNotEmpty()) {  
                                                segments[segments.size - 1] = lastSegmentText  
                                                paragraphs[paragraphs.size - 1] = segments.joinToString("，")  
                                                recognizedText = paragraphs.joinToString("\n")  
                                            }  
                                        }  
                                    }  
                                      
                                    // 重置标点状态并开始晋升计时  
                                    punctuationState = PunctuationState.NONE  
                                    startPunctuationTimer()  
                                }  
  
                                buffer = arrayListOf()  
                                offset = 0  
                                currentSegmentText = ""  
  
                                coroutineScope.launch {  
                                    scrollState.scrollTo(scrollState.maxValue)  
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
              
            // 停止录音时也停止标点计时  
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
                    if (recognizedText.isNotBlank()) {  
                        clipboardManager.setText(AnnotatedString(recognizedText))  
  
                        Toast.makeText(  
                            context,  
                            "Copied to clipboard",  
                            Toast.LENGTH_SHORT  
                        )  
                            .show()  
                    } else {  
                        Toast.makeText(  
                            context,  
                            "Nothing to copy",  
                            Toast.LENGTH_SHORT  
                        )  
                            .show()  
  
                    }  
                },  
                onClearButtonClick = {  
                    recognizedText = ""  
                    punctuationState = PunctuationState.NONE  
                    lastSegmentText = ""  
                    stopPunctuationTimer()  
                }  
            )  
  
            if (recognizedText.isNotBlank()) {  
                Text(  
                    text = recognizedText,  
                    modifier = Modifier  
                        .fillMaxWidth()  
                        .fillMaxHeight()  
                        .padding(16.dp)  
                        .verticalScroll(scrollState)  
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
