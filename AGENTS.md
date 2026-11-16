# remote-dictation

Offline push-to-talk dictation for Fire TV sticks running LineageOS (Android TV 13, no Google
services): hold the remote's voice (Alexa) button, speak, release, and the text is typed into
the focused field. Kotlin, Gradle multi-module. See `README.md` for the architecture and the
hardware facts it relies on.

## Core Invariants
- Keep changes small, focused, and test-verified.
- Offline and private: the app must not request `android.permission.INTERNET`. Check the merged
  manifest (`app/build/intermediates/merged_manifest*/**/AndroidManifest.xml`) after adding a dependency.
- `core` is plain Kotlin/JVM with no Android imports, so the session logic stays unit-testable.
- New engines or actions are new modules implementing a `core` contract (`AudioSource`,
  `SpeechEngine`/`Recognizer`, `TranscriptAction`); route new behaviours through `Mode` + `Router`.
- The remote only streams audio while the button is held: `AudioSource.read` must never block,
  and capture must never share a thread with recognition (the recognizer is slower than real
  time on the stick, and a starved `AudioRecord` silently drops audio).
- Only one speech engine is loaded at a time (`DictationService.loadEngine`): the stick has ~900 MB
  of RAM and Whisper alone needs ~150 MB. Timings taken while another model is resident are skewed by swap.
- Never commit broken builds, failed tests, or unverified changes.

## Essential Commands
Tool versions (JDK 21, Gradle) come from `mise.toml`; the Android SDK path goes in `local.properties`.
- Unit tests: `mise exec -- ./gradlew :core:test :stt-whisper:testDebugUnitTest`
- Debug APK: `mise exec -- ./gradlew :app:assembleDebug` (downloads and hash-checks whisper.cpp and both models on first build; needs the NDK version pinned in `stt-whisper/build.gradle.kts`)
- Install on the TV: `adb connect <tv-ip>:5555 && adb install -r app/build/outputs/apk/debug/app-debug.apk`
- Logs: `adb logcat -s RemoteDictation VoskAPI` (Whisper logs how long each phrase took)
- Benchmark Whisper models on the device: `tools/bench-whisper.py` (stop the app first, it holds a model in RAM)
- Debug builds keep the last 20 utterances as WAVs: `adb exec-out run-as dev.remotedictation ls files/utterances`

## Testing on the device
There is no emulator equivalent of the voice remote: the push-to-talk path (button, BLE mic,
recognition, typing) is only verified on a real Fire TV Stick with its remote, with a human
holding the button. Record what was said and compare it with the typed text or the saved WAVs.

<!-- aginit:start — managed by npx @antomanc/aginit (re-run to refresh) -->
## Bootstrap & First Session
When the user asks to "bootstrap this project" or kicks off a new initiative:
1. Understand product intent and ask 2-3 essential questions to resolve ambiguity.
2. Draft or refine canonical context in `PRODUCT.md` and `DESIGN.md` (or `docs/adr/`).
3. Plan in small verifiable increments and implement test-first using installed skills.

## Guardrails (karpathy-guidelines)
- **Think before coding**: state assumptions and trade-offs; ask instead of guessing.
- **Simplicity first**: minimum code that solves the problem; no speculative features or abstractions.
- **Surgical changes**: touch only what the task needs; no silent scope creep or drive-by refactors.
- **Goal-driven execution**: define a verifiable success check first, then loop until it passes.

## Task Routing
Load only what the task needs. Skills live in `.agents/skills/` (linked into `.claude/skills/` for Claude Code).

| Task | Read / Use |
| --- | --- |
| Automated unit & E2E testing | `docs/agents/testing.md` |
| Live UI verification (Web / Mobile) | `docs/agents/ui-verification.md` |
| Engineering workflow & skills | `docs/agents/workflows.md` |
| Prose & Copy (docs, README, UI text) | `humanizer` skill |

## Anti-Rationalization
| Excuse | Rule |
| --- | --- |
| "It's a small UI tweak, visual verification isn't needed." | Forbidden. Run agent-device on the simulator/emulator, inspect the snapshot/screenshot, and confirm no runtime errors. |
| "Unit tests pass, so the feature works." | Insufficient. Unit tests do not verify wiring or rendering. Exercise the feature end to end (integration or smoke run) before marking complete. |
<!-- aginit:end -->
