#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
#
# Fuzzes every Jazzer target in turn (test plan 3.4, release check G-09).
#
#   bash scripts/fuzz.sh            # 60 s per target: the CI budget
#   bash scripts/fuzz.sh 3600       # 1 hour per target: the release budget
#   bash scripts/fuzz.sh 60 deflate # one target by name
#
# Jazzer fuzzes one target per run, so each target gets its own Gradle run. A finding fails that run
# and leaves its input under src/fuzz/resources/…Inputs/<target>/, where the regression mode of
# `./gradlew check` replays it from then on: commit it with the fix (test plan 3.4). The working corpus
# in each module's .cifuzz-corpus/ is a local cache and is not committed.
set -u
seconds="${1:-60}"
only="${2:-}"
cd "$(dirname "$0")/../android"

targets=(
  "core:songformat SongFormatFuzz songParser"
  "core:songformat SongFormatFuzz setlistParser"
  "core:songformat SongFormatFuzz libraryFile"
  "core:songformat SongFormatFuzz releaseIndex"
  "core:songformat SongFormatFuzz archiveManifest"
  "core:songformat SongFormatFuzz archive"
  "core:protocol ProtocolFuzz cbor"
  "core:protocol ProtocolFuzz payloads"
  "core:protocol ProtocolFuzz deflate"
  "core:protocol ProtocolFuzz beacon"
  "core:protocol ProtocolFuzz joinLink"
  "core:protocol ProtocolFuzz reassembly"
  "core:protocol ProtocolFuzz erasure"
  "core:protocol ProtocolFuzz gattWrites"
)

failed=0
for t in "${targets[@]}"; do
  read -r module class method <<< "$t"
  [ -n "$only" ] && [ "$only" != "$method" ] && continue
  start=$(date +%s)
  if JAZZER_FUZZ=1 ./gradlew -q ":$module:fuzzTest" --tests "*$class.$method*" "-Pfuzz.seconds=$seconds" > /dev/null 2>&1; then
    result="pass"
  else
    result="FINDING (see $module build/reports/tests/fuzzTest)"
    failed=$((failed + 1))
  fi
  printf '%-16s %-16s %4ds  %s\n' "$module" "$method" "$(( $(date +%s) - start ))" "$result"
done
echo "findings: $failed"
[ "$failed" -eq 0 ]
