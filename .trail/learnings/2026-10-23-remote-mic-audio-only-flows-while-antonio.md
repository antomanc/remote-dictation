---
title: Remote mic audio only flows while the voice button is held; capture needs its own thread
author: Antonio
date: 2026-10-23
branch: main
cost: { minutes: 30, tokens: 139526 }
id: l-5c9ba45d
scope: repo
status: active
src: agent
touches:
  - ".github/workflows/ci.yml"
  - ".agents/skills/agent-device/SKILL.md"
  - ".agents/skills/ask-matt/PHASE-BOUNDARIES.md"
  - ".agents/skills/ask-matt/SKILL.md"
  - ".agents/skills/ask-matt/agents/openai.yaml"
  - ".agents/skills/code-review/SKILL.md"
  - ".agents/skills/code-review/agents/openai.yaml"
  - ".agents/skills/codebase-design/DEEPENING.md"
  - ".agents/skills/codebase-design/DESIGN-IT-TWICE.md"
  - ".agents/skills/codebase-design/SKILL.md"
---

## Decided
- Push-to-talk on KEYCODE_SEARCH: the kernel driver (hid-ftv-bleremote.c) starts the remote's Opus stream on key down and stops it on release, so recognition happens per hold (core/.../VoiceSession.kt).
- Capture runs on its own thread feeding a queue to the recognizer thread; AudioRecord buffer is 2 s (capture-remote/.../RemoteMicSource.kt).

## Tried and ruled out
- Reading the mic on the recognizer thread: Vosk is slower than real time on the stick, AudioRecord overflowed and silently dropped audio (84% WER). Regression test: slowRecognizerDoesNotLoseAudio.
- Always-listening voice keyboards (Sayboard) and MediaRecorder/WAV finalization: they block or stall because no audio arrives while the button is up.

## Watch out
- AudioSource.read must never block; use READ_NON_BLOCKING.
- Whisper (planned stt-whisper) is not streaming, so it gets no live partials; keep capture decoupled the same way.
