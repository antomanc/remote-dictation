# Remote Dictation

**Hold the voice button on your Fire TV remote, speak, release: the words are typed into
whatever text field is selected.** Fully offline, no Google services, no root.

[![CI](https://github.com/antomanc/remote-dictation/actions/workflows/ci.yml/badge.svg)](https://github.com/antomanc/remote-dictation/actions/workflows/ci.yml)
[![License](https://img.shields.io/github/license/antomanc/remote-dictation)](LICENSE)

> **Early prototype.** It works end to end on the hardware below, accuracy is "decent", and
> there is no signed release yet: you build the APK yourself.

## Who it is for

People who replaced Fire OS on a Fire TV Stick with LineageOS and kept the stock Alexa voice
remote. Without Amazon's software the voice button does nothing, and the usual fixes (Google
Assistant via GApps, always-listening voice keyboards) either need Google services or simply
do not work with this remote. Remote Dictation turns the button into push-to-talk typing.

Tested on a **Fire TV Stick 3rd gen / Lite (`sheldon`)** running
[LineageOS 20 v0.5](https://github.com/amazon-oss/releases/releases) (Android TV 13, no GApps)
with its stock voice remote. Other `amazon-oss` builds that ship the same remote driver
probably work; reports welcome.

## Features

- **Push-to-talk:** hold the voice button, speak, release: the words are typed about two
  seconds later.
- **Types anywhere:** appends to the focused text field in any app (browser, store search,
  streaming apps) through an accessibility service.
- **Accurate and offline:** speech recognition runs on the stick with
  [whisper.cpp](https://github.com/ggml-org/whisper.cpp) (OpenAI's Whisper base.en). The app
  asks for the microphone only; it does not even have the Internet permission.
- **Two engines:** Whisper by default, or [Vosk](https://alphacephei.com/vosk/) on the setup
  screen if you prefer seeing the words while you speak, at the cost of more mistakes.
- **Fast phrases:** start the next phrase while the previous one is still being recognized;
  they are typed in order.

## Install

1. Build the APK (see [Build](#build)) and install it:
   ```bash
   adb connect <tv-ip>:5555
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
2. Open **Remote Dictation** on the TV, choose **Allow microphone**, then **Open Accessibility
   settings** and turn on Remote Dictation. Or over adb:
   ```bash
   adb shell pm grant dev.remotedictation android.permission.RECORD_AUDIO
   adb shell settings put secure enabled_accessibility_services dev.remotedictation/.DictationService
   ```
3. Select a text field, hold the voice button and talk.

The speech models are English for now and bundled in the APK, which is why it is about
150 MB.

## How it works

On LineageOS the remote's voice button arrives as a plain `KEYCODE_SEARCH`, and the ROM's
kernel driver ([`hid-ftv-bleremote.c`](https://github.com/amazon-oss/android_kernel_amazon_mt8695/blob/HEAD/drivers/hid/hid-ftv-bleremote.c))
tells the remote to start streaming Opus audio while that key is held and to stop on release.
The audio HAL decodes it to 16 kHz mono and exposes it to apps as a Bluetooth LE input. Audio
therefore only flows while the button is down: ideal for push-to-talk, and the reason
always-listening voice keyboards stall on this hardware.

```
Voice button ─► capture-remote ─► stt-whisper ─► Router ─► action-type-text
 (hold/release)  (AudioRecord,     or stt-vosk    (Mode →    (accessibility:
                  own thread)      (offline)       action)     append to focused field)
```

| Module | What it is |
|---|---|
| `core` | Plain Kotlin: the `AudioSource`, `SpeechEngine`/`Recognizer` and `TranscriptAction` contracts, the `Router`, and `VoiceSession`, the push-to-talk state machine. Unit tested. |
| `capture-remote` | `AudioSource` reading the remote's mic with `AudioRecord`, never blocking. |
| `stt-whisper` | `SpeechEngine` using whisper.cpp, compiled from a pinned release with the NDK. The model (`base.en` q8_0) is downloaded and hash-checked at build time and bundled in the APK. |
| `stt-vosk` | `SpeechEngine` using Vosk, with the same download-and-bundle approach for its model. |
| `action-type-text` | `TranscriptAction` appending the transcript to the focused text field. |
| `app` | The accessibility service (button handling, status banner) and a TV setup screen. |

Capture and recognition run on separate threads: on the stick the recognizer is sometimes
slower than real time, and reading the microphone from the same thread made Android drop most
of each phrase. New speech engines or actions are new modules implementing a `core` contract;
the `Router` maps a `Mode` to its action, so another mode (for example an assistant) can be
added without touching dictation.

## Accuracy and speed

Measured on a Fire TV Stick (`sheldon`) with real recordings from its remote; word error rate,
lower is better, and the wait between releasing the button and the text appearing:

| Engine | Model size | WER | Wait after release |
|---|---|---|---|
| **Whisper base.en q8_0 (default)** | 82 MB | 2% | 1.6–2.8 s |
| Vosk small en-us 0.15 | 40 MB | 26% | none (words appear while you speak) |

Whisper was dictated live twice (52 words, one miss: "jazz" → "just"); Vosk was scored on the
same phrases. Getting Whisper this fast on a 32-bit stick took a few tricks, see
[ADR 2](docs/adr/0002-whisper-cpp-as-default-engine.md). `tools/bench-whisper.py` measures
other models on your own recordings.

## Build

Needs JDK 21 and the Android SDK (platform 35). Gradle installs the NDK and CMake versions it
needs for whisper.cpp if the SDK licences are accepted. With [mise](https://mise.jdx.dev/) the
JDK and Gradle versions come from `mise.toml`.

```bash
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
mise exec -- ./gradlew :core:test :stt-whisper:testDebugUnitTest :app:assembleDebug
```

Debug builds keep the last 20 utterances as WAV files, handy for comparing engines:

```bash
adb exec-out run-as dev.remotedictation ls files/utterances
```

## Roadmap

- [x] **Whisper engine** (`stt-whisper`, whisper.cpp base.en), now the default.
- [ ] More languages (Italian first) with a model picker on the setup screen.
- [ ] Signed release APK on GitHub Releases, then F-Droid / IzzyOnDroid.
- [ ] Optional on-device assistant mode (e.g. double-press and hold), as a separate `Mode`.

## Contributing

Issues and pull requests are welcome, especially reports from other Fire TV models and ROMs.
Agent setup (`AGENTS.md`, skills) was bootstrapped with
[aginit](https://github.com/antomanc/aginit).

## License

[Apache License 2.0](LICENSE). Bundles whisper.cpp (MIT, [ggml-org](https://github.com/ggml-org/whisper.cpp)),
OpenAI's Whisper base.en model weights in ggml format (MIT), the Vosk library and the
`vosk-model-small-en-us-0.15` model (Apache 2.0, [Alpha Cephei](https://alphacephei.com/vosk/))
and JNA (Apache 2.0). The
banner icon is Google's Material "mic" icon (Apache 2.0).
