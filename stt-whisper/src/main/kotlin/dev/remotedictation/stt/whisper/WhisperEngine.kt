package dev.remotedictation.stt.whisper

import android.content.Context
import android.content.res.AssetManager
import android.os.SystemClock
import android.util.Log
import dev.remotedictation.core.Recognizer
import dev.remotedictation.core.SpeechEngine

/**
 * Offline recognition with whisper.cpp and the model bundled under assets/whisper-model.bin.
 *
 * Whisper transcribes a whole clip at once, so there are no partial results while the button is
 * held: the audio is buffered and recognized on [Recognizer.finish].
 */
class WhisperEngine private constructor(private var context: Long) : SpeechEngine {

    override fun newRecognizer(sampleRate: Int): Recognizer {
        require(sampleRate == WhisperTuning.SAMPLE_RATE) { "Whisper needs 16 kHz audio, got $sampleRate" }
        return WhisperRecognizer()
    }

    @Synchronized
    override fun close() {
        if (context != 0L) nativeFree(context)
        context = 0
    }

    @Synchronized
    private fun transcribe(samples: FloatArray): String {
        check(context != 0L) { "Whisper engine is closed" }
        val start = SystemClock.elapsedRealtime()
        val bytes = nativeTranscribe(
            context, samples, THREADS,
            WhisperTuning.audioContext(samples.size), WhisperTuning.maxTokens(samples.size),
        ) ?: error("Whisper could not transcribe the recording")
        Log.i(TAG, "Whisper: %.1f s of audio in %d ms".format(samples.size.toFloat() / WhisperTuning.SAMPLE_RATE, SystemClock.elapsedRealtime() - start))
        return WhisperTuning.clean(bytes.decodeToString())
    }

    private inner class WhisperRecognizer : Recognizer {
        private var samples = ShortArray(WhisperTuning.SAMPLE_RATE * 5)
        private var size = 0

        override fun accept(samples: ShortArray, count: Int): String {
            if (size + count > this.samples.size) this.samples = this.samples.copyOf(maxOf(size + count, this.samples.size * 2))
            samples.copyInto(this.samples, size, 0, count)
            size += count
            return ""
        }

        override fun finish(): String {
            if (WhisperTuning.tooShort(size)) return ""
            return transcribe(FloatArray(size) { samples[it] / 32768f })
        }

        override fun close() = Unit
    }

    companion object {
        private const val TAG = "RemoteDictation"
        private const val ASSET = "whisper-model.bin"
        private val THREADS = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)

        init {
            System.loadLibrary("whisper_jni")
        }

        /** Loads the model from the APK. Takes about a second on a Fire TV Stick: call off the main thread. */
        fun load(context: Context): WhisperEngine {
            val handle = nativeLoad(context.assets, ASSET)
            check(handle != 0L) { "Could not load the Whisper model" }
            return WhisperEngine(handle)
        }

        @JvmStatic private external fun nativeLoad(assets: AssetManager, asset: String): Long
        @JvmStatic private external fun nativeFree(context: Long)
        @JvmStatic private external fun nativeTranscribe(context: Long, samples: FloatArray, threads: Int, audioCtx: Int, maxTokens: Int): ByteArray?
    }
}
