package com.k2fsa.sherpa.onnx.simulate.streaming.asr.screens  
  
import androidx.compose.runtime.Composable  
import androidx.compose.foundation.layout.Box  
import androidx.compose.foundation.layout.Column  
import androidx.compose.foundation.layout.Spacer  
import androidx.compose.foundation.layout.fillMaxSize  
import androidx.compose.foundation.layout.height  
import androidx.compose.foundation.layout.padding  
import androidx.compose.material3.Text  
import androidx.compose.ui.Modifier  
import androidx.compose.ui.unit.dp  
import androidx.compose.ui.unit.sp  
  
@Composable  
fun HelpScreen() {  
    Box(modifier = Modifier.fillMaxSize()) {  
        Column(  
            modifier = Modifier.padding(8.dp)  
        ) {  
            Text(  
                "这是一个基于 sherpa-onnx 的技术构建的安卓离线语音识别 App。目标是最大限度地保证用户隐私，同时带来高效的语音输入体验。建议将其固定到系统的智能侧边栏中，方便随时取用。",  
                fontSize = 10.sp  
            )  
            Spacer(modifier = Modifier.height(10.dp))  
            Text(  
                "应用已开源，由 Walter 构建。",  
                fontSize = 10.sp  
            )  
  
            Spacer(modifier = Modifier.height(10.dp))  
            Text(  
                "感谢 csukuangfj 和小米集团。",  
                fontSize = 10.sp  
            )  
        }  
    }  
}
