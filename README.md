# Remote Dictation

Offline push-to-talk dictation for Fire TV sticks running LineageOS: hold the voice (Alexa)
button on the remote, speak, release, and the words are typed into the selected text field.
No Google services, no cloud, no root.

Tested on a Fire TV Stick 3rd gen / Lite (`sheldon`) with
[LineageOS 20 v0.5](https://github.com/amazon-oss/releases/releases) and its stock voice remote.

## How it works

On LineageOS the remote's voice button arrives as a plain `KEYCODE_SEARCH`. The ROM's kernel
driver ([`hid-ftv-bleremote.c`](https://github.com/amazon-oss/android_kernel_amazon_mt8695/blob/HEAD/drivers/hid/hid-ftv-bleremote.c))
starts the remote's microphone stream while that button is held and stops it on release; the
audio HAL decodes it to 16 kHz mono and exposes it as a Bluetooth LE input. Audio therefore
only flows while the button is down, which suits push-to-talk (and is why always-listening
voice keyboards stall on this hardware).

```
Voice button ─► capture-remote ─► stt-vosk ─► Router ─► action-type-text
 (hold/release)  (AudioRecord,     (offline    (Mode →    (accessibility:
                  non-blocking)     Vosk)       action)     set focused field text)
```

| Module | What it is |
|---|---|
| `core` | Plain Kotlin: the `AudioSource`, `SpeechEngine`/`Recognizer` and `TranscriptAction` contracts, the `Router`, and `VoiceSession` (the push-to-talk state machine). Unit tested. |
| `capture-remote` | `AudioSource` reading the remote's mic with `AudioRecord`. |
| `stt-vosk` | `SpeechEngine` using [Vosk](https://alphacephei.com/vosk/). The model is downloaded and hash-checked at build time and bundled in the APK. |
| `action-type-text` | `TranscriptAction` appending the transcript to the focused text field. |
| `app` | The accessibility service (button handling, status banner) and a TV setup screen. |

New engines or actions are new modules implementing a `core` contract; the `Router` maps a
`Mode` to the action, so e.g. an assistant could be added as another mode later.

## Build

Needs JDK 21 and the Android SDK (platform 35). With [mise](https://mise.jdx.dev/) the JDK and
Gradle versions come from `mise.toml`.

```bash
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
mise exec -- ./gradlew :core:test :app:assembleDebug
```

## Install

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Open **Remote Dictation** on the TV, allow the microphone, and turn the service on in
Accessibility settings (or `adb shell settings put secure enabled_accessibility_services dev.remotedictation/.DictationService`).

## License

Apache License 2.0. The bundled Vosk model (`vosk-model-small-en-us-0.15`) is also Apache 2.0.
