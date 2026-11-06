package com.aksharvel.tactile

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Records the child's voice as 16 kHz mono PCM, which is what the recogniser wants.
 * Nothing is written to disk and nothing leaves the phone: the samples live in
 * memory for one attempt and are then dropped.
 */
class Listener(context: Context) {

    companion object {
        const val SAMPLE_RATE = 16_000
        /** Stops on its own so a stuck mic can never hang a lesson. */
        const val MAX_MILLIS = 5_000
        /** Below this there is nothing worth scoring. */
        const val MIN_MILLIS = 250

        fun hasPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
    }

    private val app = context.applicationContext

    @Volatile
    private var recording = false

    /** Loudness of the last chunk, 0f..1f, for the pulsing microphone button. */
    @Volatile
    var level: Float = 0f
        private set

    val isRecording: Boolean get() = recording

    private val minBuffer = maxOf(
        AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        ),
        SAMPLE_RATE / 4
    )

    /**
     * Records until [stop] is called or [MAX_MILLIS] elapses.
     * Blocking - call from a background dispatcher, never the main thread.
     *
     * @return samples as floats in -1f..1f, or null if the mic was unavailable
     *         or the clip was too short to score.
     */
    @SuppressLint("MissingPermission")
    fun record(): FloatArray? {
        if (!hasPermission(app) || recording) return null

        val r = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuffer * 2
            )
        } catch (e: Exception) {
            return null
        }

        if (r.state != AudioRecord.STATE_INITIALIZED) {
            r.release()
            return null
        }

        recording = true
        level = 0f

        val maxSamples = SAMPLE_RATE * MAX_MILLIS / 1000
        val out = FloatArray(maxSamples)
        var n = 0
        val chunk = ShortArray(minBuffer / 2)

        return try {
            r.startRecording()
            while (recording && n < maxSamples) {
                val got = r.read(chunk, 0, chunk.size)
                if (got <= 0) break
                var sum = 0.0
                for (i in 0 until got) {
                    if (n >= maxSamples) break
                    val v = chunk[i] / 32768f
                    out[n++] = v
                    sum += (v * v).toDouble()
                }
                val rms = sqrt(sum / got).toFloat()
                level = (level * 0.6f + rms * 4f * 0.4f).coerceIn(0f, 1f)
            }
            if (n < SAMPLE_RATE * MIN_MILLIS / 1000) null else out.copyOf(n)
        } catch (e: Exception) {
            null
        } finally {
            recording = false
            level = 0f
            try { r.stop() } catch (e: Exception) { /* already stopped */ }
            r.release()
        }
    }

    /** Ends the current recording; [record] returns shortly after. */
    fun stop() {
        recording = false
    }
}
