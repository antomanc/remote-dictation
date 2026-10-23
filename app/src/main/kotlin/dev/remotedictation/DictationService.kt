package dev.remotedictation

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioManager
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import dev.remotedictation.action.typetext.TypeIntoFocusedField
import dev.remotedictation.capture.RemoteMicSource
import dev.remotedictation.core.Mode
import dev.remotedictation.core.Router
import dev.remotedictation.core.SpeechEngine
import dev.remotedictation.core.VoiceSession
import java.io.File
import java.util.concurrent.Executors

/**
 * Holds the voice pipeline for as long as the accessibility service is enabled.
 * Holding the remote's voice (Alexa) button dictates into the focused text field.
 */
class DictationService : AccessibilityService(), VoiceSession.Listener {

    private lateinit var overlay: StatusOverlay

    /** Loads and swaps engines one at a time, off the main thread. */
    private val loader = Executors.newSingleThreadExecutor()

    @Volatile
    private var session: VoiceSession? = null
    private var engine: SpeechEngine? = null

    @Volatile
    private var engineChoice: EngineChoice? = null

    // Kept in a field: SharedPreferences only holds listeners weakly.
    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> loadEngine() }

    override fun onServiceConnected() {
        overlay = StatusOverlay(this)
        EngineChoice.preferences(this).registerOnSharedPreferenceChangeListener(settingsListener)
        loadEngine()
    }

    /** Loads the engine picked on the setup screen, first unloading the previous one: the stick has little RAM. */
    private fun loadEngine() = loader.execute {
        val choice = EngineChoice.current(this)
        if (choice == engineChoice) return@execute
        val switching = engineChoice != null
        session?.let { previous ->
            session = null
            previous.release()
            previous.await()
        }
        engine?.close()
        engine = null
        engineChoice = null
        if (switching) overlay.show(getString(R.string.status_loading), StatusOverlay.Style.INFO)
        try {
            val loaded = choice.load(this).also { engine = it }
            val mic = RemoteMicSource(getSystemService(AudioManager::class.java))
            session = VoiceSession(
                source = if (BuildConfig.DEBUG) UtteranceRecorder(mic, File(filesDir, "utterances")) else mic,
                engine = loaded,
                router = Router(mapOf(Mode.DICTATE to TypeIntoFocusedField(this))),
                listener = this,
            )
            engineChoice = choice
            if (switching) overlay.hide()
        } catch (e: Exception) {
            Log.e(TAG, "Could not load the $choice speech model", e)
            overlay.show(getString(R.string.status_model_failed), StatusOverlay.Style.ERROR)
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

    override fun onCaptured() {
        // Whisper only starts once the button is up; say so instead of leaving "Listening" on screen.
        if (engineChoice?.streamsPartials == false && session?.isCapturing != true) {
            overlay.show(getString(R.string.status_transcribing), StatusOverlay.Style.INFO)
        }
    }

    override fun onResult(text: String, handled: Boolean) = when {
        // The next phrase is already being captured: keep its "Listening" banner up.
        session?.isCapturing == true -> Unit
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
        EngineChoice.preferences(this).unregisterOnSharedPreferenceChangeListener(settingsListener)
        session?.release()
        loader.shutdown()
        overlay.hide()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "RemoteDictation"

        /** The Alexa button: LineageOS maps it to SEARCH (HID consumer usage 0x221). */
        const val VOICE_KEY = KeyEvent.KEYCODE_SEARCH
    }
}
