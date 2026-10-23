package dev.remotedictation.core

import kotlin.concurrent.thread

/**
 * Push-to-talk: [press] starts capturing and recognizing on a worker thread, [release] stops
 * capturing, finishes recognition and routes the transcript.
 */
class VoiceSession(
    private val source: AudioSource,
    private val engine: SpeechEngine,
    private val router: Router,
    private val listener: Listener,
    private val idleWaitMillis: Long = 10,
) {
    interface Listener {
        fun onListening() {}
        fun onPartial(text: String) {}
        fun onResult(text: String, handled: Boolean) {}
        fun onError(error: Throwable) {}
    }

    @Volatile
    private var holding = false
    private var worker: Thread? = null

    val isActive: Boolean
        get() = worker?.isAlive == true

    /** Button down. Ignored while the previous utterance is still being finished. */
    fun press(mode: Mode = Mode.DICTATE) {
        if (isActive) return
        holding = true
        worker = thread(name = "voice-session") { run(mode) }
    }

    /** Button up. */
    fun release() {
        holding = false
    }

    /** Waits for the current utterance to be delivered (used by tests and shutdown). */
    fun await(timeoutMillis: Long = 0) {
        worker?.join(timeoutMillis)
    }

    private fun run(mode: Mode) {
        try {
            engine.newRecognizer(source.sampleRate).use { recognizer ->
                capture(recognizer)
                val text = recognizer.finish().trim()
                val handled = text.isNotEmpty() && router.dispatch(mode, text)
                listener.onResult(text, handled)
            }
        } catch (e: Exception) {
            listener.onError(e)
        }
    }

    private fun capture(recognizer: Recognizer) {
        val buffer = ShortArray(source.sampleRate / 10)
        source.open()
        try {
            listener.onListening()
            var last = ""
            while (holding) {
                val count = source.read(buffer)
                if (count == 0) {
                    Thread.sleep(idleWaitMillis)
                    continue
                }
                val partial = recognizer.accept(buffer, count)
                if (partial != last) {
                    last = partial
                    listener.onPartial(partial)
                }
            }
            // Keep what arrived between the last read and the button release.
            while (true) {
                val count = source.read(buffer)
                if (count == 0) break
                recognizer.accept(buffer, count)
            }
        } finally {
            source.close()
        }
    }
}
