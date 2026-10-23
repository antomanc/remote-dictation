package dev.remotedictation.capture

import android.annotation.SuppressLint
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import dev.remotedictation.core.AudioSource

/**
 * The Fire TV voice remote's microphone.
 *
 * On LineageOS for Fire TV the kernel (hid-ftv-bleremote) starts the remote's audio stream
 * while the voice button is held and the audio HAL exposes it as a Bluetooth LE input at
 * 16 kHz mono. Reads are non-blocking because no samples arrive once the button is released.
 */
class RemoteMicSource(
    private val audioManager: AudioManager,
    override val sampleRate: Int = 16_000,
) : AudioSource {
    private var record: AudioRecord? = null

    @SuppressLint("MissingPermission") // The app checks RECORD_AUDIO before starting a session.
    override fun open() {
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, CHANNEL, ENCODING)
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            CHANNEL,
            ENCODING,
            maxOf(minBuffer, sampleRate), // at least 0.5 s of 16-bit audio
        )
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            error("Microphone unavailable (is RECORD_AUDIO granted?)")
        }
        remoteInput()?.let(audioRecord::setPreferredDevice)
        audioRecord.startRecording()
        record = audioRecord
    }

    override fun read(buffer: ShortArray): Int {
        val audioRecord = checkNotNull(record) { "read() before open()" }
        val count = audioRecord.read(buffer, 0, buffer.size, AudioRecord.READ_NON_BLOCKING)
        check(count >= 0) { "Microphone read failed ($count)" }
        return count
    }

    override fun close() {
        record?.run {
            stop()
            release()
        }
        record = null
    }

    private fun remoteInput(): AudioDeviceInfo? =
        audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .firstOrNull { it.type == AudioDeviceInfo.TYPE_BLE_HEADSET }

    private companion object {
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }
}
