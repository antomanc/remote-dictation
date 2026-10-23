package dev.remotedictation.core

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Push-to-talk: [press] starts capturing and recognizing, [release] stops capturing, finishes
 * recognition and routes the transcript.
 *
 * Capture and recognition run on separate threads: on a Fire TV Stick the recognizer is
 * sometimes slower than real time, and if it also had to read the microphone, the audio
 * buffer would overflow and words would be lost. Captured chunks are queued instead and
 * recognition catches up after the button is released.
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

    // press() and release() come from one thread (key events); the rest is per utterance.
    private var held: AtomicBoolean? = null
    private var lastCapture: Thread? = null

    @Volatile
    private var lastWorker: Thread? = null

    /** True while any utterance is still being captured or recognized. */
    val isActive: Boolean
        get() = lastWorker?.isAlive == true

    /**
     * Button down. Capture starts right away even if the previous utterance is still being
     * recognized; utterances are recognized one at a time and delivered in order.
     */
    fun press(mode: Mode = Mode.DICTATE) {
        if (held?.get() == true) return
        val holding = AtomicBoolean(true)
        val chunks = LinkedBlockingQueue<Chunk>()
        val previousCapture = lastCapture
        val previousWorker = lastWorker
        held = holding
        lastCapture = thread(name = "voice-capture") {
            previousCapture?.join() // the source is opened by one utterance at a time
            capture(holding, chunks)
        }
        lastWorker = thread(name = "voice-recognize") {
            previousWorker?.join()
            recognize(mode, chunks)
        }
    }

    /** Button up. */
    fun release() {
        held?.set(false)
    }

    /** Waits for every pending utterance to be delivered (used by tests and shutdown). */
    fun await(timeoutMillis: Long = 0) {
        lastWorker?.join(timeoutMillis)
    }

    private fun recognize(mode: Mode, chunks: LinkedBlockingQueue<Chunk>) {
        try {
            engine.newRecognizer(source.sampleRate).use { recognizer ->
                var last = ""
                while (true) {
                    when (val chunk = chunks.take()) {
                        is Chunk.Audio -> {
                            val partial = recognizer.accept(chunk.samples, chunk.samples.size)
                            if (partial != last) {
                                last = partial
                                listener.onPartial(partial)
                            }
                        }
                        is Chunk.Failed -> throw chunk.error
                        Chunk.End -> break
                    }
                }
                val text = recognizer.finish().trim()
                val handled = text.isNotEmpty() && router.dispatch(mode, text)
                listener.onResult(text, handled)
            }
        } catch (e: Exception) {
            listener.onError(e)
        }
    }

    private fun capture(holding: AtomicBoolean, chunks: LinkedBlockingQueue<Chunk>) {
        val buffer = ShortArray(source.sampleRate / 10)
        try {
            source.open()
            try {
                listener.onListening()
                while (holding.get()) {
                    if (!readInto(buffer, chunks)) Thread.sleep(idleWaitMillis)
                }
                // Keep what arrived between the last read and the button release.
                while (readInto(buffer, chunks)) Unit
            } finally {
                source.close()
            }
            chunks.put(Chunk.End)
        } catch (e: Exception) {
            chunks.put(Chunk.Failed(e))
        }
    }

    private fun readInto(buffer: ShortArray, chunks: LinkedBlockingQueue<Chunk>): Boolean {
        val count = source.read(buffer)
        if (count == 0) return false
        chunks.put(Chunk.Audio(buffer.copyOf(count)))
        return true
    }

    private sealed interface Chunk {
        class Audio(val samples: ShortArray) : Chunk
        class Failed(val error: Exception) : Chunk
        data object End : Chunk
    }
}
