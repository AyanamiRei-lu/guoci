package com.guoci.vocab3500.platform

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * 单词发音，用系统 TTS。
 *
 * 为什么不接在线发音：这个 App 离线可用，而在线发音需要 INTERNET 权限 + 网络 + 一个
 * 第三方服务，换来的只是音质更好。系统 TTS 在绝大多数国行/国际版机器上都带 en-US
 * 语音包；没有也不能崩 —— 引擎缺失时 [available] 保持 false，界面直接不显示喇叭按钮。
 */
class Speaker(private val context: Context) {

    var available by mutableStateOf(false)
        private set
    var speaking by mutableStateOf(false)
        private set

    private var tts: TextToSpeech? = null
    private var ready = false

    fun start() {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status != TextToSpeech.SUCCESS) {
                available = false
                return@TextToSpeech
            }
            val engine = tts ?: return@TextToSpeech
            val result = engine.setLanguage(Locale.US)
            ready = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            available = ready
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) { speaking = true }
                override fun onDone(utteranceId: String?) { speaking = false }
                @Deprecated("按平台签名实现")
                override fun onError(utteranceId: String?) { speaking = false }
            })
        }
    }

    fun speak(text: String) {
        if (!ready) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "w-${text.hashCode()}")
    }

    fun stop() {
        tts?.stop()
        speaking = false
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
        available = false
    }
}
