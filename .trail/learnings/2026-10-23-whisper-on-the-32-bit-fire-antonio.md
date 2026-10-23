---
title: "Whisper on the 32-bit Fire TV stick is only fast with a shortened audio_ctx, q8_0 and -mfpu=neon-vfpv4"
author: Antonio
date: 2026-10-23
branch: main
cost: { minutes: 1, tokens: 0 }
id: l-e70410d4
scope: repo
status: active
src: agent
---

## Decided
- `stt-whisper/src/main/kotlin/dev/remotedictation/stt/whisper/WhisperTuning.kt` and `stt-whisper/src/main/cpp/whisper_jni.cpp`: whisper.cpp base.en q8_0, greedy, no fallback, single segment, audio_ctx = clip + 1.5 s, token cap. In-app: 1.6-2.8 s per phrase, ~2% WER.
- `stt-whisper/src/main/cpp/CMakeLists.txt` compiles armeabi-v7a with -mfpu=neon-vfpv4 (~3x faster encode than the NDK's default ARMv7 NEON) and forces CMAKE_BUILD_TYPE Release even for debug APKs; unoptimized ggml is unusable on the stick.

## Tried and ruled out
- Full 30 s window (default audio_ctx): 35 s for a 3 s clip.
- audio_ctx with < ~1.5 s margin: Whisper repeats the phrase until the token limit (8-60 s).
- f16 models: ~10x slower than q8_0 on 32-bit ARM. q5_1: same accuracy, ~20% slower than q8_0.
- -march=armv8-a in AArch32: ggml 1.9.5 fails to build (redefinition of vcvtnq_s32_f32) without patches.

## Watch out
- `tools/bench-whisper.py`: benchmark only with the app stopped; with its model resident the stick swaps and timings double or triple.
- `whisper_jni.cpp` / `DictationService.loadEngine`: switching Vosk -> Whisper left ~110 MB of Vosk's freed small blocks in the process until mallopt(M_PURGE) was added before loading Whisper.
- `CMakeLists.txt`: BUILD_SHARED_LIBS etc. must be CACHE FORCE before add_subdirectory, or ggml is built as separate .so files.
