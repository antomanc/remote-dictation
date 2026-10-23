#!/usr/bin/env python3
"""Measure Whisper models on a real device: word error rate and time per clip.

Runs whisper.cpp's CLI over adb with the settings the app uses (greedy decoding, no fallback,
audio context sized to the clip plus a 1.5 s margin, see WhisperTuning.kt).

Usage:
    tools/bench-whisper.py --cli whisper-cli --clips DIR MODEL.bin [MODEL.bin ...]

DIR holds 16 kHz mono WAV recordings (debug builds save them, see the README) and a
refs.tsv with one "<file name><TAB><what was said>" line per clip. Keep recordings out of git.

Build the CLI for the stick with the same flags as the app (-mfpu=neon-vfpv4 matters, ~3x):
    cmake -S whisper.cpp -B build -G Ninja \\
      -DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake \\
      -DANDROID_ABI=armeabi-v7a -DANDROID_PLATFORM=android-26 -DCMAKE_BUILD_TYPE=Release \\
      -DBUILD_SHARED_LIBS=OFF -DGGML_NATIVE=OFF -DGGML_OPENMP=OFF \\
      -DCMAKE_C_FLAGS=-mfpu=neon-vfpv4 -DCMAKE_CXX_FLAGS=-mfpu=neon-vfpv4
    cmake --build build --target whisper-cli

Stop the app first (adb shell am force-stop dev.remotedictation): with its model loaded, the
stick runs out of RAM and swapping makes every timing meaningless. Pick the device with
ANDROID_SERIAL as usual for adb.
"""
import argparse
import math
import re
import subprocess
import wave
from pathlib import Path

DEVICE_DIR = "/data/local/tmp/whisper-bench"


def adb(*args: str) -> str:
    return subprocess.run(["adb", *args], check=True, capture_output=True, text=True).stdout


def words(text: str) -> list[str]:
    return re.sub(r"[^a-z' ]", " ", text.lower()).split()


def edit_distance(ref: list[str], hyp: list[str]) -> int:
    row = list(range(len(hyp) + 1))
    for i, r in enumerate(ref, 1):
        prev, row[0] = row[0], i
        for j, h in enumerate(hyp, 1):
            prev, row[j] = row[j], min(row[j] + 1, row[j - 1] + 1, prev + (r != h))
    return row[-1]


def audio_context(seconds: float) -> int:
    return min(1500, math.ceil((seconds + 1.5) * 50 / 64) * 64)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--cli", type=Path, required=True, help="whisper-cli built for the device")
    parser.add_argument("--clips", type=Path, required=True, help="folder with WAVs and refs.tsv")
    parser.add_argument("--threads", type=int, default=4)
    parser.add_argument("models", type=Path, nargs="+")
    args = parser.parse_args()

    refs = dict(line.split("\t", 1) for line in (args.clips / "refs.tsv").read_text().splitlines() if line.strip())
    adb("shell", "mkdir", "-p", DEVICE_DIR)
    for path in [args.cli, *args.models, *(args.clips / name for name in refs)]:
        adb("push", str(path), f"{DEVICE_DIR}/{path.name}")
    adb("shell", "chmod", "755", f"{DEVICE_DIR}/{args.cli.name}")

    for model in args.models:
        errors = total_words = 0
        times = []
        for name, ref in refs.items():
            with wave.open(str(args.clips / name)) as clip:
                seconds = clip.getnframes() / clip.getframerate()
            out = adb("shell", f"cd {DEVICE_DIR} && ./{args.cli.name} -m {model.name} -f {name} -t {args.threads} "
                               f"-ac {audio_context(seconds)} -bs 1 -bo 1 -nf -mc 0 -nt 2>&1")
            hyp = " ".join(line.strip() for line in out.splitlines() if line.startswith(" "))
            # The app loads the model once, so its load time is not part of the wait.
            elapsed = (float(re.search(r"total time =\s+([\d.]+)", out)[1]) - float(re.search(r"load time =\s+([\d.]+)", out)[1])) / 1000
            errors += edit_distance(words(ref), words(hyp))
            total_words += len(words(ref))
            times.append(elapsed)
            print(f"  {seconds:4.1f} s audio -> {elapsed:4.1f} s  {hyp}")
        print(f"{model.name}: WER {100 * errors / total_words:.0f}%, mean {sum(times) / len(times):.1f} s, worst {max(times):.1f} s\n")


if __name__ == "__main__":
    main()
