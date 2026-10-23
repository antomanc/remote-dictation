package dev.remotedictation.stt.whisper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhisperTuningTest {
    private fun samples(seconds: Double) = (seconds * WhisperTuning.SAMPLE_RATE).toInt()

    @Test
    fun audioContextCoversTheClipPlusMarginInBlocksOf64() {
        assertEquals(256, WhisperTuning.audioContext(samples(3.1))) // (3.1 + 1.5) * 50 = 230
        assertEquals(320, WhisperTuning.audioContext(samples(4.5))) // 300
    }

    @Test
    fun audioContextNeverExceedsTheFullWindow() {
        assertEquals(1500, WhisperTuning.audioContext(samples(40.0)))
    }

    @Test
    fun strayPressesAreTooShort() {
        assertTrue(WhisperTuning.tooShort(samples(0.2)))
        assertFalse(WhisperTuning.tooShort(samples(0.5)))
    }

    @Test
    fun cleanStripsOnlyTheFinalFullStop() {
        assertEquals("Open Netflix and find Stranger Things", WhisperTuning.clean(" Open Netflix and find Stranger Things."))
        assertEquals("What's the weather like tomorrow?", WhisperTuning.clean(" What's the weather like tomorrow?"))
        assertEquals("Wait...", WhisperTuning.clean("Wait..."))
        assertEquals("U.S. news", WhisperTuning.clean("U.S. news"))
    }

    @Test
    fun cleanDropsNonSpeechAnnotations() {
        assertEquals("", WhisperTuning.clean(" [BLANK_AUDIO]"))
        assertEquals("", WhisperTuning.clean("(music)"))
    }
}
