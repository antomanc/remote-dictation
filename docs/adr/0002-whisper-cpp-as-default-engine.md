# 2. whisper.cpp base.en as the default speech engine

Date: 2026-10-23

## Status
Accepted

## Context
Vosk small en-us got about a quarter of the words wrong on recordings from the Fire TV remote.
On the PC, Whisper base.en got 8% wrong, but the stick is a 32-bit Android on four
Cortex-A53 cores with about 900 MB of RAM. The goal was at most 3 s between releasing the button
and the text appearing, for a typical 3 to 5 s phrase.

Measured on the stick with whisper.cpp 1.9.5, five recordings from the remote:
- Default settings: tiny.en took 35 s for a 3 s clip, almost all of it encoding Whisper's fixed
  30 s window.
- Encoding only the clip (`audio_ctx`) brought that down to a few seconds, but with less than
  ~1.5 s of margin Whisper missed the end of the phrase and repeated it until the token limit.
- f16 models are unusably slow on 32-bit ARM. q8_0 beat q5_1 by about 20% at equal accuracy.
- Building for `-mfpu=neon-vfpv4` (fused multiply-add) instead of the NDK's plain ARMv7 NEON
  made encoding about 3x faster. An ARMv8-in-AArch32 build would need patches to ggml.

Result with all of the above, base.en q8_0, in the app: 1.6 to 2.8 s per phrase, 1 wrong word
in 52 over two rounds of dictation (Vosk: 4 wrong in 26).

## Decision
- New `stt-whisper` module: whisper.cpp built from a pinned, hash-checked release tarball with
  the NDK and linked statically into one JNI library; `ggml-base.en-q8_0.bin` bundled in the
  APK uncompressed and streamed from assets (no copy to storage).
- Greedy decoding, no temperature fallback, a single segment, `audio_ctx` = clip + 1.5 s, and a
  token cap proportional to the clip length (`WhisperTuning`).
- Whisper is the default. Vosk stays as an option on the setup screen for live words while
  speaking; only the selected engine is loaded, as the stick has little RAM.

## Consequences
- Dictation is far more accurate, at the cost of no live words while the button is held
  ("Transcribing…" is shown after release).
- The APK grows to about 147 MB (82 MB for the Whisper model) and building needs the Android
  NDK and CMake, which Gradle installs automatically when the SDK licences are accepted.
- The armeabi-v7a library requires a VFPv4 CPU (Cortex-A7 or newer), true of every Fire TV
  Stick LineageOS supports.
- English only for now; Italian needs a multilingual model (next milestone).
- `tools/bench-whisper.py` reproduces the measurements on a device.
