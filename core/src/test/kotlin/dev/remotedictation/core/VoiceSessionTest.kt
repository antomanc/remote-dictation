package dev.remotedictation.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

class VoiceSessionTest {
    /** Hands out one chunk per entry in [chunks], then reports no data (button released). */
    private class FakeSource(vararg chunks: Int) : AudioSource {
        private val pending = ArrayDeque(chunks.toList())
        var opened = false
        var closed = false
        override val sampleRate = 16_000
        override fun open() { opened = true }
        override fun read(buffer: ShortArray): Int = pending.removeFirstOrNull() ?: 0
        override fun close() { closed = true }
    }

    /** Recognizes each sample count as one word, so the transcript reveals what was fed. */
    private class FakeEngine : SpeechEngine {
        var closed = false
        override fun newRecognizer(sampleRate: Int) = object : Recognizer {
            val words = mutableListOf<String>()
            override fun accept(samples: ShortArray, count: Int): String {
                words += "w$count"
                return words.joinToString(" ")
            }
            override fun finish() = words.joinToString(" ")
            override fun close() { closed = true }
        }
    }

    private class Recording : VoiceSession.Listener {
        val results = CopyOnWriteArrayList<Pair<String, Boolean>>()
        val errors = CopyOnWriteArrayList<Throwable>()
        override fun onResult(text: String, handled: Boolean) { results += text to handled }
        override fun onError(error: Throwable) { errors += error }
    }

    private fun session(source: AudioSource, engine: SpeechEngine, typed: MutableList<String>, listener: Recording) =
        VoiceSession(source, engine, Router(mapOf(Mode.DICTATE to TranscriptAction { typed += it; true })), listener, idleWaitMillis = 1)

    @Test
    fun releaseDeliversEverythingCapturedToTheAction() {
        val source = FakeSource(3, 5)
        val engine = FakeEngine()
        val typed = mutableListOf<String>()
        val listener = Recording()
        val session = session(source, engine, typed, listener)

        session.press()
        Thread.sleep(50)
        session.release()
        session.await(1_000)

        assertEquals(listOf("w3 w5"), typed)
        assertEquals(listOf("w3 w5" to true), listener.results)
        assertTrue(source.opened && source.closed)
        assertTrue(engine.closed)
    }

    @Test
    fun emptyTranscriptIsReportedButNotTyped() {
        val typed = mutableListOf<String>()
        val listener = Recording()
        val session = session(FakeSource(), FakeEngine(), typed, listener)

        session.press()
        session.release()
        session.await(1_000)

        assertTrue(typed.isEmpty())
        assertEquals(listOf("" to false), listener.results)
    }

    @Test
    fun pressWhileHoldingIsIgnored() {
        val typed = mutableListOf<String>()
        val listener = Recording()
        val session = session(FakeSource(4), FakeEngine(), typed, listener)

        session.press()
        session.press()
        assertTrue(session.isCapturing)
        session.release()
        assertFalse(session.isCapturing)
        session.await(1_000)

        assertEquals(1, listener.results.size)
        assertFalse(session.isActive)
    }

    @Test
    fun pressWhileStillRecognizingStartsTheNextUtteranceInOrder() {
        // Each open() delivers one utterance: the first 3 samples long, the second 7.
        val source = object : AudioSource {
            val utterances = ArrayDeque(listOf(3, 7))
            var pending: Int? = null
            override val sampleRate = 16_000
            override fun open() { pending = utterances.removeFirst() }
            override fun read(buffer: ShortArray): Int = pending.also { pending = null } ?: 0
            override fun close() {}
        }
        val slowEngine = object : SpeechEngine {
            override fun newRecognizer(sampleRate: Int) = object : Recognizer {
                var text = ""
                override fun accept(samples: ShortArray, count: Int): String {
                    Thread.sleep(100) // the first utterance is still recognizing at the second press
                    text = "w$count"
                    return text
                }
                override fun finish() = text
                override fun close() {}
            }
        }
        val typed = java.util.Collections.synchronizedList(mutableListOf<String>())
        val session = session(source, slowEngine, typed, Recording())

        session.press()
        Thread.sleep(20)
        session.release()
        session.press()
        Thread.sleep(20)
        session.release()
        session.await(5_000)

        assertEquals(listOf("w3", "w7"), typed.toList())
    }

    @Test
    fun slowRecognizerDoesNotLoseAudio() {
        // Like AudioRecord: chunks arrive in real time into a tiny buffer and are dropped when
        // nobody reads them soon enough.
        val source = object : AudioSource {
            val buffered = java.util.concurrent.ArrayBlockingQueue<Int>(2)
            override val sampleRate = 16_000
            override fun open() {
                thread {
                    for (chunk in 1..20) {
                        buffered.offer(chunk) // silently dropped when full
                        Thread.sleep(5)
                    }
                }
            }
            override fun read(buffer: ShortArray): Int = buffered.poll() ?: 0
            override fun close() {}
        }
        val slowEngine = object : SpeechEngine {
            override fun newRecognizer(sampleRate: Int) = object : Recognizer {
                val words = mutableListOf<String>()
                override fun accept(samples: ShortArray, count: Int): String {
                    Thread.sleep(20) // slower than the audio arrives
                    words += "w$count"
                    return words.joinToString(" ")
                }
                override fun finish() = words.joinToString(" ")
                override fun close() {}
            }
        }
        val typed = mutableListOf<String>()
        val session = session(source, slowEngine, typed, Recording())

        session.press()
        Thread.sleep(300) // the button is held well past the end of the audio
        session.release()
        session.await(5_000)

        assertEquals((1..20).joinToString(" ") { "w$it" }, typed.single())
    }

    @Test
    fun sourceFailureIsReportedAndSourceClosed() {
        val source = object : AudioSource {
            var closed = false
            override val sampleRate = 16_000
            override fun open() {}
            override fun read(buffer: ShortArray): Int = throw IllegalStateException("mic gone")
            override fun close() { closed = true }
        }
        val listener = Recording()
        val session = session(source, FakeEngine(), mutableListOf(), listener)

        session.press()
        session.await(1_000)

        assertEquals("mic gone", listener.errors.single().message)
        assertTrue(source.closed)
    }
}
