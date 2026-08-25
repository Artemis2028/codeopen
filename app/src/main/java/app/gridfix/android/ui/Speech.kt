package app.gridfix.android.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Minimal text-to-speech wrapper: initializes asynchronously, speaks when
 * ready, stays silent otherwise. Uses the device TTS engine — fully offline
 * when the phone has an offline voice installed (most do by default).
 */
class Speech(context: Context) {

    private var ready = false
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            ready = true
        }
    }

    fun speak(text: String) {
        if (!ready) return
        runCatching {
            tts.language = Locale.US
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "gridfix")
        }
    }

    fun shutdown() {
        runCatching { tts.shutdown() }
    }
}
