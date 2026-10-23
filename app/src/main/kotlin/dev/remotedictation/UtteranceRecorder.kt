package dev.remotedictation

import dev.remotedictation.core.AudioSource
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Debug aid: passes audio through and saves each utterance as a WAV in [dir] (keeping the
 * newest [keep]), so recognition engines can be compared offline on real remote audio.
 */
class UtteranceRecorder(
    private val source: AudioSource,
    private val dir: File,
    private val keep: Int = 20,
) : AudioSource by source {
    private val pcm = ByteArrayOutputStream()

    override fun open() {
        pcm.reset()
        source.open()
    }

    override fun read(buffer: ShortArray): Int {
        val count = source.read(buffer)
        if (count > 0) {
            val bytes = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until count) bytes.putShort(buffer[i])
            pcm.write(bytes.array())
        }
        return count
    }

    override fun close() {
        source.close()
        if (pcm.size() == 0) return
        dir.mkdirs()
        File(dir, "utterance-${System.currentTimeMillis()}.wav").writeBytes(wavHeader(pcm.size()) + pcm.toByteArray())
        dir.listFiles().orEmpty().sortedByDescending { it.name }.drop(keep).forEach { it.delete() }
    }

    private fun wavHeader(dataSize: Int): ByteArray =
        ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + dataSize); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
            putInt(sampleRate); putInt(sampleRate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(dataSize)
        }.array()
}
