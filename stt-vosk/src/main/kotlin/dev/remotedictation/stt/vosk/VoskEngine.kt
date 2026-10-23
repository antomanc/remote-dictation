package dev.remotedictation.stt.vosk

import android.content.Context
import android.content.res.AssetManager
import dev.remotedictation.core.Recognizer
import dev.remotedictation.core.SpeechEngine
import org.json.JSONObject
import org.vosk.Model
import java.io.File

/** Offline recognition with the Vosk model bundled under assets/vosk-model. */
class VoskEngine private constructor(private val model: Model) : SpeechEngine {

    override fun newRecognizer(sampleRate: Int): Recognizer =
        VoskRecognizer(org.vosk.Recognizer(model, sampleRate.toFloat()))

    override fun close() = model.close()

    companion object {
        private const val ASSET_DIR = "vosk-model"

        /**
         * Copies the model out of the APK (Vosk needs real files) once per app install or
         * update, then loads it. Takes a few seconds on a Fire TV Stick: call off the main thread.
         */
        fun load(context: Context): VoskEngine {
            val target = File(context.noBackupFilesDir, ASSET_DIR)
            val stamp = File(target, ".installed")
            val version = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime.toString()
            if (!stamp.exists() || stamp.readText() != version) {
                target.deleteRecursively()
                copyAssets(context.assets, ASSET_DIR, target)
                stamp.writeText(version)
            }
            return VoskEngine(Model(target.path))
        }

        private fun copyAssets(assets: AssetManager, path: String, target: File) {
            val children = assets.list(path).orEmpty()
            if (children.isEmpty()) {
                target.parentFile?.mkdirs()
                assets.open(path).use { input -> target.outputStream().use(input::copyTo) }
                return
            }
            children.forEach { copyAssets(assets, "$path/$it", File(target, it)) }
        }
    }
}

/**
 * Vosk ends an utterance at each pause, so finished segments are kept and joined with the
 * partial one to give the whole transcript while the button is held.
 */
private class VoskRecognizer(private val recognizer: org.vosk.Recognizer) : Recognizer {
    private val segments = mutableListOf<String>()

    override fun accept(samples: ShortArray, count: Int): String {
        val partial = if (recognizer.acceptWaveForm(samples, count)) {
            keep(recognizer.result, "text")
            ""
        } else {
            JSONObject(recognizer.partialResult).optString("partial")
        }
        return (segments + partial).filter(String::isNotBlank).joinToString(" ")
    }

    override fun finish(): String {
        keep(recognizer.finalResult, "text")
        return segments.joinToString(" ")
    }

    override fun close() = recognizer.close()

    private fun keep(json: String, key: String) {
        JSONObject(json).optString(key).takeIf(String::isNotBlank)?.let(segments::add)
    }
}
