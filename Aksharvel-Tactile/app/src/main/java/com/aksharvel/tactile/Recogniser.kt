package com.aksharvel.tactile

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Marathi speech recognition, entirely on the phone, via sherpa-onnx running an
 * AI4Bharat IndicConformer CTC model from assets.
 *
 * Reached by reflection on purpose, so the app still builds and runs with no
 * sherpa-onnx dependency and no model bundled. Without them [status] settles on
 * UNAVAILABLE, the speaking tab hides itself, and the rest of Aksharvel is
 * untouched. Add the library and the model and it switches itself on.
 *
 * Nothing here opens a socket. The app has no INTERNET permission and must keep
 * it that way: the model is read from assets, never fetched.
 */
class Recogniser(context: Context) {

    enum class Status { LOADING, READY, UNAVAILABLE }

    companion object {
        /** Folder inside app/src/main/assets holding the model and its tokens. */
        const val ASSET_DIR = "asr"
        const val MODEL_FILE = "model.int8.onnx"
        const val TOKENS_FILE = "tokens.txt"
    }

    var status by mutableStateOf(Status.LOADING)
        private set

    /** Set when loading failed, for the diagnostics line on the profile screen. */
    var detail: String? = null
        private set

    val isReady: Boolean get() = status == Status.READY

    private val app = context.applicationContext
    private var engine: Any? = null
    private var recogniseMethod: java.lang.reflect.Method? = null

    /** Blocking and slow. Call once from a background dispatcher at startup. */
    fun load() {
        if (status == Status.READY) return

        val assets = try {
            app.assets.list(ASSET_DIR)?.toSet() ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }
        if (MODEL_FILE !in assets || TOKENS_FILE !in assets) {
            fail("No model in assets/$ASSET_DIR - speaking mode is off.")
            return
        }

        try {
            val cfgCls = Class.forName("com.k2fsa.sherpa.onnx.OfflineRecognizerConfig")
            val modelCfgCls = Class.forName("com.k2fsa.sherpa.onnx.OfflineModelConfig")
            val nemoCls = Class.forName("com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig")
            val recCls = Class.forName("com.k2fsa.sherpa.onnx.OfflineRecognizer")

            val nemo = nemoCls.getConstructor(String::class.java)
                .newInstance("$ASSET_DIR/$MODEL_FILE")

            val modelCfg = modelCfgCls.getDeclaredConstructor().newInstance()
            set(modelCfg, "nemoCtc", nemo)
            set(modelCfg, "tokens", "$ASSET_DIR/$TOKENS_FILE")
            set(modelCfg, "numThreads", 2)
            set(modelCfg, "modelType", "nemo_ctc")

            val cfg = cfgCls.getDeclaredConstructor().newInstance()
            set(cfg, "modelConfig", modelCfg)

            engine = recCls
                .getConstructor(android.content.res.AssetManager::class.java, cfgCls)
                .newInstance(app.assets, cfg)

            recogniseMethod = recCls.methods.firstOrNull {
                it.name == "recognise" || it.name == "recognize"
            }
            if (recogniseMethod == null) {
                fail("sherpa-onnx is present but has no recognise method.")
                return
            }
            status = Status.READY
            detail = null
        } catch (e: ClassNotFoundException) {
            fail("sherpa-onnx is not in the build - speaking mode is off.")
        } catch (e: Throwable) {
            fail("${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /**
     * Transcribes one attempt. Blocking; call off the main thread.
     * @param samples 16 kHz mono, -1f..1f, from [Listener].
     * @return the recognised Marathi text, or null if recognition was not possible.
     */
    fun transcribe(samples: FloatArray): String? {
        val r = engine ?: return null
        val m = recogniseMethod ?: return null
        return try {
            val text = m.invoke(r, samples, Listener.SAMPLE_RATE.toFloat())
                ?: m.invoke(r, samples, Listener.SAMPLE_RATE)
            text?.toString()
        } catch (e: Throwable) {
            null
        }
    }

    fun release() {
        val r = engine ?: return
        try {
            r.javaClass.methods.firstOrNull { it.name == "release" }?.invoke(r)
        } catch (e: Throwable) {
            // nothing useful to do
        }
        engine = null
        recogniseMethod = null
    }

    private fun fail(why: String) {
        status = Status.UNAVAILABLE
        detail = why
    }

    private fun set(target: Any, field: String, value: Any) {
        val f = target.javaClass.getDeclaredField(field)
        f.isAccessible = true
        f.set(target, value)
    }
}
