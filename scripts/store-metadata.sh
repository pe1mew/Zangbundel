#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Writes F-Droid's store metadata, fastlane/metadata/android/, from the Play listing in docs/store-listing/.
# docs/store-listing/ is the one source: edit there, then run this. With --check it writes nothing,
# and exits 1 when fastlane/ is out of step or a text is over F-Droid's limit (docs/FDROID-RUNBOOK.md Step 5).
#   en-US and nl-NL: title.txt, short_description.txt, full_description.txt, changelogs/<versionCode>.txt
#     from the sections "## App name", "## Short description", "## Full description" and each
#     "### <versionName> (<versionCode>)" under "## Release notes"
#   en-US only (F-Droid falls back to en-US): images/icon.png, featureGraphic.png, phoneScreenshots/1.png …
set -u
cd "$(dirname "$0")/.." || exit 1
export LC_ALL=C.UTF-8

src=docs/store-listing
dst=fastlane/metadata/android
fail=0
say() { printf '%s\n' "$*"; }

# The body of the "## <heading>" section of a listing file, CRs and surrounding blank lines removed.
section() {
  awk -v h="$2" '
    { sub(/\r$/, "") }
    /^## / { on = (index($0, "## " h) == 1); next }
    on { buf[++n] = $0 }
    END {
      s = 1; while (s <= n && buf[s] == "") s++
      e = n; while (e >= s && buf[e] == "") e--
      for (i = s; i <= e; i++) print buf[i]
    }' "$1"
}

# Writes one text file and checks its length in characters against F-Droid's limit.
text() { # file limit
  local f=$1 limit=$2 n
  cat > "$f"
  n=$(tr -d '\n' < "$f" | wc -m)
  if [ "$n" -gt "$limit" ]; then say "FAIL: ${f#"$out"/} has $n characters, F-Droid allows $limit"; fail=1; fi
  if [ "$n" -eq 0 ]; then say "FAIL: ${f#"$out"/} is empty"; fail=1; fi
}

out=$(mktemp -d) || exit 1
trap 'rm -rf "$out"' EXIT

for loc in en-US nl-NL; do
  md=$src/$loc.md
  [ -f "$md" ] || { say "FAIL: $md not found"; exit 1; }
  d=$out/$loc
  mkdir -p "$d/changelogs"
  section "$md" "App name" | text "$d/title.txt" 50
  section "$md" "Short description" | text "$d/short_description.txt" 80
  section "$md" "Full description" | text "$d/full_description.txt" 4000
  notes=$(section "$md" "Release notes")
  codes=$(printf '%s\n' "$notes" | sed -n 's/^### [0-9.]* (\([0-9]\{10\}\))$/\1/p')
  [ -n "$codes" ] || { say "FAIL: $md has no release note headed \"### <versionName> (<versionCode>)\""; fail=1; }
  for c in $codes; do
    printf '%s\n' "$notes" | awk -v c="($c)" '
      /^### / { on = (substr($0, length($0) - length(c) + 1) == c); next }
      on { buf[++n] = $0 }
      END {
        s = 1; while (s <= n && buf[s] == "") s++
        e = n; while (e >= s && buf[e] == "") e--
        for (i = s; i <= e; i++) print buf[i]
      }' | text "$d/changelogs/$c.txt" 500
  done
done

img=$out/en-US/images
mkdir -p "$img/phoneScreenshots"
cp "$src/graphics/icon-512.png" "$img/icon.png"
cp "$src/graphics/feature-1024x500.png" "$img/featureGraphic.png"
i=0
for p in "$src"/graphics/phone-[0-9]*-*.png; do
  i=$((i + 1)); cp "$p" "$img/phoneScreenshots/$i.png"
done
[ "$i" -gt 0 ] || { say "FAIL: no phone screenshots in $src/graphics"; fail=1; }

if [ "${1:-}" = "--check" ]; then
  if ! diff -r --strip-trailing-cr "$out" "$dst" > /dev/null 2>&1; then
    say "FAIL: $dst is out of step with $src; run bash scripts/store-metadata.sh"
    diff -rq --strip-trailing-cr "$out" "$dst" 2>&1 | sed "s#$out#(generated)#g"
    fail=1
  fi
  [ "$fail" -eq 0 ] && say "PASS: $dst matches $src ($i screenshots)"
  exit "$fail"
fi

[ "$fail" -eq 0 ] || exit 1
rm -rf "$dst" && mkdir -p "$dst" && cp -r "$out"/. "$dst"/
say "Wrote $dst from $src: en-US, nl-NL, $i screenshots"
