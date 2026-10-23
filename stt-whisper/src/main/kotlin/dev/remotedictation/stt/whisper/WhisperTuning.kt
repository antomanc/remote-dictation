package dev.remotedictation.stt.whisper

import kotlin.math.ceil

/** The numbers that make Whisper usable on a Fire TV Stick, measured on recordings from its remote. */
internal object WhisperTuning {
    const val SAMPLE_RATE = 16_000

    /** Whisper's full window: 1500 encoder frames = 30 s, 50 frames per second. */
    private const val FULL_AUDIO_CONTEXT = 1500
    private const val FRAMES_PER_SECOND = 50

    /**
     * Encoding the full 30 s window took over 30 s on the stick even for a 3 s phrase, so only
     * the clip plus a margin is encoded. With less than ~1.5 s of margin Whisper missed the end
     * of the phrase and repeated it.
     */
    fun audioContext(samples: Int): Int {
        val frames = (seconds(samples) + 1.5) * FRAMES_PER_SECOND
        return minOf(FULL_AUDIO_CONTEXT, (ceil(frames / 64) * 64).toInt())
    }

    /** A cap well above normal speech (~4 tokens/s) that still stops a repetition loop early. */
    fun maxTokens(samples: Int): Int = 16 + (seconds(samples) * 8).toInt()

    /** Shorter clips are stray presses; Whisper tends to invent text ("Thank you.") for them. */
    fun tooShort(samples: Int): Boolean = seconds(samples) < 0.3

    /**
     * Drops non-speech annotations like "[BLANK_AUDIO]" or "(music)", and the final full stop,
     * which is noise in the search boxes most TV dictation goes into.
     */
    fun clean(text: String): String {
        val trimmed = text.trim()
        if (ANNOTATION.matches(trimmed)) return ""
        return if (trimmed.endsWith('.') && !trimmed.endsWith("..")) trimmed.dropLast(1) else trimmed
    }

    private val ANNOTATION = Regex("""^[\[(].*[\])]$""")

    private fun seconds(samples: Int) = samples.toDouble() / SAMPLE_RATE
}
