package dev.remotedictation

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.RadioGroup
import android.widget.TextView

/** TV-friendly setup: grant the microphone, enable the accessibility service, pick the engine. */
class SetupActivity : Activity() {

    private lateinit var micStatus: TextView
    private lateinit var serviceStatus: TextView
    private lateinit var micButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)
        micStatus = findViewById(R.id.mic_status)
        serviceStatus = findViewById(R.id.service_status)
        micButton = findViewById(R.id.mic_button)
        micButton.setOnClickListener { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1) }
        findViewById<Button>(R.id.service_button).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<RadioGroup>(R.id.engine_group).apply {
            check(if (EngineChoice.current(context) == EngineChoice.WHISPER) R.id.engine_whisper else R.id.engine_vosk)
            // The service sees the change and swaps the model.
            setOnCheckedChangeListener { _, id ->
                EngineChoice.save(context, if (id == R.id.engine_whisper) EngineChoice.WHISPER else EngineChoice.VOSK)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        refresh()
    }

    private fun refresh() {
        val micGranted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        micStatus.setText(if (micGranted) R.string.setup_mic_ok else R.string.setup_mic_missing)
        micButton.isEnabled = !micGranted
        serviceStatus.setText(if (serviceEnabled()) R.string.setup_service_ok else R.string.setup_service_missing)
        (if (micGranted) findViewById(R.id.service_button) else micButton).requestFocus()
    }

    private fun serviceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val ours = ComponentName(this, DictationService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == ours }
    }
}
