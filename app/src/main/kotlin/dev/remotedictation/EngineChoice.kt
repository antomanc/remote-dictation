package dev.remotedictation

import android.content.Context
import android.content.SharedPreferences
import dev.remotedictation.core.SpeechEngine
import dev.remotedictation.stt.vosk.VoskEngine
import dev.remotedictation.stt.whisper.WhisperEngine

/** The speech engine dictation uses, picked on the setup screen and loaded by the service. */
enum class EngineChoice(val streamsPartials: Boolean) {
    /** More accurate; transcribes after the button is released. */
    WHISPER(streamsPartials = false),

    /** Shows words while you speak, makes more mistakes. */
    VOSK(streamsPartials = true);

    /** Loads the model; takes a second or more, so call it off the main thread. */
    fun load(context: Context): SpeechEngine = when (this) {
        WHISPER -> WhisperEngine.load(context)
        VOSK -> VoskEngine.load(context)
    }

    companion object {
        private const val KEY = "engine"

        fun preferences(context: Context): SharedPreferences =
            context.getSharedPreferences("settings", Context.MODE_PRIVATE)

        fun current(context: Context): EngineChoice =
            entries.find { it.name == preferences(context).getString(KEY, null) } ?: WHISPER

        fun save(context: Context, choice: EngineChoice) {
            preferences(context).edit().putString(KEY, choice.name).apply()
        }
    }
}
