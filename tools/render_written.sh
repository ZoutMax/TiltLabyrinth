#!/usr/bin/env bash
# Renders the two written pieces (the opening and the victory theme) with the game's own
# synthesizer and stores them in the app as Ogg Vorbis. Run it after changing either score, or after
# changing the orchestra's sound and bumping Synth.WRITTEN_VERSION: the unit tests fail until then.
# Needs oggenc (Debian/Ubuntu: apt install vorbis-tools).
set -euo pipefail
cd "$(dirname "$0")/.."
out="$PWD/build/written"
rm -rf "$out"; mkdir -p "$out"
./gradlew :app:testReleaseUnitTest --tests 'com.megapro.labyrinth.audio.WrittenPiecesTest.render' -PrenderOut="$out" --rerun -q
for name in opening victory; do
  oggenc -Q -q 6 -o "app/src/main/res/raw/$name.ogg" "$out/$name.wav"
done
cp "$out/written_scores.txt" tools/written_scores.txt
ls -l app/src/main/res/raw/opening.ogg app/src/main/res/raw/victory.ogg
