package dev.remotedictation.core

/**
 * A push-to-talk microphone delivering 16-bit mono PCM.
 *
 * The Fire TV remote only streams audio while its voice button is held, so [read] must never
 * block waiting for samples: it returns 0 when none have arrived yet.
 */
interface AudioSource {
    val sampleRate: Int

    fun open()

    /** Copies available samples into [buffer] and returns how many; 0 when none are ready. */
    fun read(buffer: ShortArray): Int

    fun close()
}

/** Turns audio into text. Implementations may be slow to create (model loading). */
interface SpeechEngine {
    fun newRecognizer(sampleRate: Int): Recognizer
}

/** One utterance worth of recognition state. */
interface Recognizer : AutoCloseable {
    /** Feeds [count] samples and returns the transcript so far (may be empty). */
    fun accept(samples: ShortArray, count: Int): String

    /** Flushes buffered audio and returns the final transcript. */
    fun finish(): String
}

/** Something to do with a finished transcript, e.g. type it or ask an assistant. */
fun interface TranscriptAction {
    /** Returns false when the action could not apply (e.g. no text field is focused). */
    fun perform(text: String): Boolean
}

/** What the user meant by talking. New modes get their own [TranscriptAction] in the [Router]. */
enum class Mode {
    DICTATE,
}

class Router(private val actions: Map<Mode, TranscriptAction>) {
    fun dispatch(mode: Mode, text: String): Boolean = actions[mode]?.perform(text) ?: false
}
