package dev.remotedictation

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.pm.PackageManager
import android.media.AudioManager
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import dev.remotedictation.action.typetext.TypeIntoFocusedField
import dev.remotedictation.capture.RemoteMicSource
import dev.remotedictation.core.Mode
import dev.remotedictation.core.Router
import dev.remotedictation.core.VoiceSession
import dev.remotedictation.stt.vosk.VoskEngine
import java.io.File
import kotlin.concurrent.thread

/**
 * Holds the voice pipeline for as long as the accessibility service is enabled.
 * Holding the remote's voice (Alexa) button dictates into the focused text field.
 */
class DictationService : AccessibilityService(), VoiceSession.Listener {

    private lateinit var overlay: StatusOverlay

    @Volatile
    private var session: VoiceSession? = null

    override fun onServiceConnected() {
        overlay = StatusOverlay(this)
        thread(name = "vosk-load") {
            try {
                val engine = VoskEngine.load(this)
                val mic = RemoteMicSource(getSystemService(AudioManager::class.java))
                session = VoiceSession(
                    source = if (BuildConfig.DEBUG) UtteranceRecorder(mic, File(filesDir, "utterances")) else mic,
                    engine = engine,
                    router = Router(mapOf(Mode.DICTATE to TypeIntoFocusedField(this))),
                    listener = this,
                )
            } catch (e: Exception) {
                Log.e(TAG, "Could not load the speech model", e)
                overlay.show(getString(R.string.status_model_failed), StatusOverlay.Style.ERROR)
            }
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != VOICE_KEY) return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            when {
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ->
                    overlay.show(getString(R.string.status_no_permission), StatusOverlay.Style.ERROR)
                session == null -> overlay.show(getString(R.string.status_loading), StatusOverlay.Style.INFO)
                else -> session?.press(Mode.DICTATE)
            }
        } else if (event.action == KeyEvent.ACTION_UP) {
            session?.release()
        }
        return true // the ROM has no other use for this key
    }

    override fun onListening() = overlay.show(getString(R.string.status_listening), StatusOverlay.Style.LISTENING)

    override fun onPartial(text: String) {
        if (text.isNotEmpty()) overlay.show(text, StatusOverlay.Style.LISTENING)
    }

    override fun onResult(text: String, handled: Boolean) = when {
        text.isEmpty() -> overlay.show(getString(R.string.status_nothing_heard), StatusOverlay.Style.INFO, hideAfterMillis = 2_000)
        handled -> overlay.hide()
        else -> overlay.show(getString(R.string.status_no_field, text), StatusOverlay.Style.INFO, hideAfterMillis = 4_000)
    }

    override fun onError(error: Throwable) {
        Log.e(TAG, "Dictation failed", error)
        overlay.show(error.message ?: error.toString(), StatusOverlay.Style.ERROR, hideAfterMillis = 4_000)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        session?.release()
    }

    override fun onDestroy() {
        session?.release()
        overlay.hide()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "RemoteDictation"

        /** The Alexa button: LineageOS maps it to SEARCH (HID consumer usage 0x221). */
        const val VOICE_KEY = KeyEvent.KEYCODE_SEARCH
    }
}
