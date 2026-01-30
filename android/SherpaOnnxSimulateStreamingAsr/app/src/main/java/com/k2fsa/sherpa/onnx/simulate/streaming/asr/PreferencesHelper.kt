package com.k2fsa.sherpa.onnx.simulate.streaming.asr  
  
import android.content.Context  
import android.content.SharedPreferences  
  
class PreferencesHelper(context: Context) {  
    private val PREFS_NAME = "com.k2fsa.sherpa.onnx.asr.preferences"  
    private val RECOGNIZED_TEXT_KEY = "recognized_text"  
      
    private val sharedPreferences: SharedPreferences =  
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)  
      
    fun saveRecognizedText(text: String) {  
        sharedPreferences.edit()  
            .putString(RECOGNIZED_TEXT_KEY, text)  
            .apply()  
    }  
      
    fun getRecognizedText(): String {  
        return sharedPreferences.getString(RECOGNIZED_TEXT_KEY, "") ?: ""  
    }  
      
    fun clearRecognizedText() {  
        sharedPreferences.edit()  
            .remove(RECOGNIZED_TEXT_KEY)  
            .apply()  
    }  
}
