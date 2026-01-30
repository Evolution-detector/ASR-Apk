package com.k2fsa.sherpa.onnx.simulate.streaming.asr  
  
import androidx.compose.material.icons.Icons  
import androidx.compose.material.icons.filled.Home  
import androidx.compose.material.icons.filled.Info  
  
object NavBarItems {  
    val BarItems = listOf(  
        BarItem(  
            titleResId = R.string.home,  
            image = Icons.Filled.Home,  
            route = "home",  
        ),  
        BarItem(  
            titleResId = R.string.help,  
            image = Icons.Filled.Info,  
            route = "help",  
        ),  
    )  
}
