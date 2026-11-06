package com.aksharvel.tactile

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

enum class VoiceStatus { LOADING, READY }

/** Asset name of a letter's recording, e.g. "अ" -> "ak_0905.mp3"; slow version ends in "_slow". */
fun clipName(letter: String, slow: Boolean = false): String =
    "ak_" + letter.map { "%04x".format(it.code) }.joinToString("_") + (if (slow) "_slow" else "") + ".mp3"

/**
 * Plays the recorded akshara clips bundled inside the APK (app/src/main/assets/audio).
 * Works offline on any phone: nothing to install, no device settings.
 * Only if a letter has no recording does it fall back to Android text-to-speech.
 */
class Speaker(context: Context) {

    var status by mutableStateOf(VoiceStatus.LOADING)
        private set

    val isReady: Boolean
        get() = status == VoiceStatus.READY

    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .build()

    private val normal = HashMap<String, Int>()
    private val slowClips = HashMap<String, Int>()
    private var pending = 0
    private var lastStream = 0

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    init {
        pool.setOnLoadCompleteListener { _, _, _ ->
            main.post {
                pending--
                if (pending <= 0) status = VoiceStatus.READY
            }
        }
        val files = try { app.assets.list("audio")?.toSet() ?: emptySet() } catch (e: Exception) { emptySet() }
        for (letter in VARNAMALA) {
            load(letter, false, files)
            load(letter, true, files)
        }
        if (pending == 0) status = VoiceStatus.READY
        if (VARNAMALA.any { it !in normal }) startFallbackTts()
    }

    private fun load(letter: String, slow: Boolean, files: Set<String>) {
        val name = clipName(letter, slow)
        if (name !in files) return
        try {
            app.assets.openFd("audio/$name").use { afd ->
                val id = pool.load(afd, 1)
                (if (slow) slowClips else normal)[letter] = id
                pending++
            }
        } catch (e: Exception) {
            // Unreadable clip: treated as missing.
        }
    }

    /** Normal speed is the recording as-is; [slow] is the "हळू" (turtle) button. */
    fun speak(letter: String, slow: Boolean = false) {
        if (!isReady) return
        if (lastStream != 0) pool.stop(lastStream)
        val id = (if (slow) slowClips[letter] else null) ?: normal[letter]
        if (id != null) {
            lastStream = pool.play(id, 1f, 1f, 1, 0, 1f)
        } else {
            speakWithTts(letter, slow)
        }
    }

    // ---- fallback for letters that have no recording yet ----

    private fun startFallbackTts() {
        tts = TextToSpeech(app) { code ->
            main.post {
                val engine = tts
                if (engine != null && code == TextToSpeech.SUCCESS) {
                    ttsReady = engine.setLanguage(Locale("mr", "IN")) >= TextToSpeech.LANG_AVAILABLE ||
                        engine.setLanguage(Locale("hi", "IN")) >= TextToSpeech.LANG_AVAILABLE
                }
            }
        }
    }

    private fun speakWithTts(letter: String, slow: Boolean) {
        val engine = tts ?: return
        if (!ttsReady) return
        engine.setSpeechRate(if (slow) 0.4f else 0.75f)
        engine.speak(SPOKEN_OVERRIDES[letter] ?: letter, TextToSpeech.QUEUE_FLUSH, null, "akshara")
    }

    fun shutdown() {
        pool.release()
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
