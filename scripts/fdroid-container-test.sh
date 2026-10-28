#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
#
# Builds the app as F-Droid does, in the fdroidserver buildserver container (FDROID-RUNBOOK Step 8), and
# compares the APK with a reference. Needs Docker; runs from Git Bash on Windows as well as on Linux.
#
#   bash scripts/fdroid-container-test.sh                          # docs/fdroid/ as it is, Binaries included
#   bash scripts/fdroid-container-test.sh --commit <sha> --no-binaries   # another pushed commit, no release APK yet
#   bash scripts/fdroid-container-test.sh --compare <apk>          # also compare with this unsigned APK
#
# fdroiddata and fdroidserver are cloned inside the container: a Windows checkout turns their symlinks into
# text files, which fdroid then refuses. `--on-server` makes fdroid run the recipe's sudo lines, as fdroiddata's
# own CI does. fdroid build exits 0 even when the build fails, so this script reads its output instead.
# The logs and the unsigned APK land in ${FDROID_TEST_OUT:-$TMPDIR/zangbundel-fdroid-test}.
set -u
app=nl.pe1mew.zangbundel.app
image=registry.gitlab.com/fdroid/fdroidserver:buildserver

if [ "${1:-}" = "--inside" ]; then
  # In the container, as user vagrant. /in holds the metadata file, /out receives the results.
  . /etc/profile
  cd /home/vagrant || exit 1
  git clone -q --depth 1 https://gitlab.com/fdroid/fdroidserver fdroidserver || exit 1
  git clone -q --depth 1 https://gitlab.com/fdroid/fdroiddata fdroiddata || exit 1
  echo "fdroidserver $(git -C fdroidserver log --format='%h %cs' -1), fdroiddata $(git -C fdroiddata log --format='%h %cs' -1)"
  export PATH="/home/vagrant/fdroidserver:$PATH" PYTHONPATH=/home/vagrant/fdroidserver
  cd fdroiddata || exit 1
  chmod 0600 config.yml
  cp "/in/$app.yml" "metadata/$app.yml"
  failed=0
  step() { echo; echo "=== $*"; "$@"; r=$?; echo "=== exit $r"; return $r; }
  step fdroid readmeta || failed=1
  step fdroid lint "$app" || failed=1
  cp "metadata/$app.yml" /tmp/before.yml
  step fdroid rewritemeta "$app" || failed=1
  echo "--- what rewritemeta changes (comments only is fine):"
  diff /tmp/before.yml "metadata/$app.yml"
  cp /tmp/before.yml "metadata/$app.yml"
  # Fails with "No matching tags found" until the first v<major>.<minor>.<patch> tag exists: reported, not counted.
  step fdroid checkupdates --allow-dirty "$app"
  step fdroid build -v --on-server --no-tarball "$app" 2>&1 | tee /tmp/build.out
  grep -qE "build failed|Could not build" /tmp/build.out && { echo "=== BUILD FAILED"; failed=1; }
  step fdroid scanner -v "$app" || failed=1
  mkdir -p /out && cp -r unsigned logs /out/ 2>/dev/null
  cp /tmp/build.out /out/build.out
  exit $failed
fi

commit=""; binaries=yes; compare=""
while [ $# -gt 0 ]; do
  case "$1" in
    --commit) commit="$2"; shift 2 ;;
    --no-binaries) binaries=no; shift ;;
    --compare) compare="$2"; shift 2 ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
done

root="$(cd "$(dirname "$0")/.." && pwd)"
out="${FDROID_TEST_OUT:-${TMPDIR:-/tmp}/zangbundel-fdroid-test}"
rm -rf "$out"; mkdir -p "$out/in" "$out/out"; chmod 777 "$out/out"
meta="$out/in/$app.yml"
cp "$root/docs/fdroid/$app.yml" "$meta"
if [ -n "$commit" ]; then
  full=$(git -C "$root" rev-parse "$commit^{commit}") || exit 2
  sed -i "s/^    commit: .*/    commit: $full/" "$meta"
fi
[ "$binaries" = no ] && sed -i 's/^Binaries: /# Binaries: /' "$meta"
echo "testing $(grep -E '^ +(- )?(versionName|versionCode|commit):' "$meta" | tr -s ' ' | tr '\n' ' ')binaries=$binaries"

# Docker Desktop on Windows needs Windows paths, and Git Bash must leave the container paths alone.
host() { if command -v cygpath > /dev/null; then cygpath -w "$1"; else printf '%s' "$1"; fi; }
MSYS_NO_PATHCONV=1 docker run --rm -u vagrant --entrypoint /bin/bash \
  -v "$(host "$out/in"):/in:ro" -v "$(host "$out/out"):/out" \
  -v "$(host "$root/scripts/fdroid-container-test.sh"):/home/vagrant/test.sh:ro" \
  "$image" /home/vagrant/test.sh --inside > "$out/fdroid.log" 2>&1
result=$?
echo "image $(docker image inspect --format '{{index .RepoDigests 0}}' "$image")"
grep -E "^=== |^fdroidserver |problems found|No matching tags" "$out/fdroid.log"

apk=$(ls "$out"/out/unsigned/*.apk 2>/dev/null | head -1)
if [ -n "$apk" ]; then
  echo "apk $(wc -c < "$apk") bytes, sha256 $(sha256sum "$apk" | cut -d' ' -f1)"
  if [ -n "$compare" ]; then
    if cmp -s "$apk" "$compare"; then echo "identical to $compare"; else echo "DIFFERS from $compare"; result=1; fi
  fi
else
  echo "no APK built"; result=1
fi
echo "log: $out/fdroid.log"
exit $result
