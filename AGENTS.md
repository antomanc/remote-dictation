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
- Never commit broken builds, failed tests, or unverified changes.

## Essential Commands
Tool versions (JDK 21, Gradle) come from `mise.toml`; the Android SDK path goes in `local.properties`.
- Unit tests: `mise exec -- ./gradlew :core:test`
- Debug APK: `mise exec -- ./gradlew :app:assembleDebug` (downloads and hash-checks the Vosk model on first build)
- Install on the TV: `adb connect <tv-ip>:5555 && adb install -r app/build/outputs/apk/debug/app-debug.apk`
- Logs: `adb logcat -s RemoteDictation VoskAPI`
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
| Codebase exploration & impact analysis | `trail ask "<query>" --source` / `trail skeleton <file>` (see the Trail section) |
| Automated unit & E2E testing | `docs/agents/testing.md` |
| Live UI verification (Web / Mobile) | `docs/agents/ui-verification.md` |
| Engineering workflow & skills | `docs/agents/workflows.md` |
| Scoping or simplifying a change | `ponytail` skill |
| Prose & Copy (docs, README, UI text) | `humanizer` skill |

## Anti-Rationalization
| Excuse | Rule |
| --- | --- |
| "I'll grep/read the whole folder to understand the module." | Forbidden. Query `trail ask "<query>" --source` or `trail callers <symbol>` first to save context tokens. |
| "It's a small UI tweak, visual verification isn't needed." | Forbidden. Run agent-device on the simulator/emulator, inspect the snapshot/screenshot, and confirm no runtime errors. |
| "Unit tests pass, so the feature works." | Insufficient. Unit tests do not verify wiring or rendering. Exercise the feature end to end (integration or smoke run) before marking complete. |
<!-- aginit:end -->

<!-- trail:start -->
## Trail — repo context graph

This repo is indexed in `.trail/cache/`: small linked markdown nodes that explain each
system and carry exact file:line spans, kept in sync with the code through git.

For ANY task here — understanding how something works, finding where code lives,
or scoping a change — get context from the graph before grepping or opening
source files. Re-ask freely (it's cheap) and reuse literal identifiers you
already have (symbol, error string, file name) as the query. New to this repo?
Run `trail map` first — a token-budgeted orientation (dir clusters, hubs,
hotspots), no LLM, no key.

- Run `trail ask "<your question>" --source` → ranked nodes with the relevant
  code spans inlined (each hit's ≤8-line crux by default; `--full` for whole
  definitions when the crux isn't enough). Match the tool to the task shape:
  for understanding or editing, the top node IS the answer — cite its
  `covers:` file:line spans and edit straight from `--source`. For
  exhaustive tasks ("every occurrence / every caller of this pattern"), ranked
  results are top-N, not complete — run `trail grep "<literal>"` instead
  (exhaustive over indexed files, grouped by enclosing symbol), falling back
  to raw `grep -rn` only for unindexed files.
- `trail skeleton <file>` → every definition's signature + span, ~10× cheaper
  than reading the file; use it to skim an API surface.
- `trail callers <symbol>` gives precomputed, exact edges — who calls this.
  Add `--direction out` for what it calls, or `--depth N` to walk
  transitively for the full blast radius. For structural questions, skip
  ranking and use this directly.
- Or browse: `.trail/cache/INDEX.md` lists every node; follow the links.
- Monorepos and folders of multiple repos rank fairly across sub-projects —
  hits carry `[scope/]` labels naming which one they're from. Narrow with
  `trail ask "<task>" --in <scope>/` once you know where you're working.

If a returned span is truncated ("+N more lines"), open the file at that exact
range before finalizing. Only open source files when a node genuinely lacks a
needed detail, and then at the exact file:line the node points to — never
re-read whole files.

After big code changes, refresh the graph with `trail build` (deterministic,
no API key, $0).
<!-- trail:end -->
