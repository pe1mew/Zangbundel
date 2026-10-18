#!/usr/bin/env bash
# Cross-document consistency check for design/.
# Exit 0 and print PASS when everything holds; exit 1 and name each failure otherwise.
# Checks:
#   1. every FR-/NFR- ID defined in the FRD is defined exactly once
#   2. every FR-/NFR- ID cited in any design/*.md is defined in the FRD
#   3. every "section N.M" reference in ble-protocol.md resolves to a heading in that file
#   4. every design doc whose header says "Depends on `design/functional-requirements.md` vX.Y"
#      names the FRD's current version
set -u
cd "$(dirname "$0")/.." || exit 1

frd=design/functional-requirements.md
fail=0
say() { printf '%s\n' "$*"; }

[ -f "$frd" ] || { say "FAIL: $frd not found"; exit 1; }

# 1. duplicate definitions
dups=$(grep -oE '^\*\*N?FR-[A-Z]+-[0-9]+\*\*' "$frd" | sort | uniq -d)
if [ -n "$dups" ]; then
  say "FAIL: requirement IDs defined more than once in $frd:"; say "$dups"; fail=1
fi

# 2. cited but undefined
undefined=""
for id in $(cat design/*.md | grep -oE 'N?FR-[A-Z]+-[0-9]{2}' | sort -u); do
  grep -q "^\*\*$id\*\*" "$frd" || undefined="$undefined $id"
done
if [ -n "$undefined" ]; then
  say "FAIL: requirement IDs cited in design/ but not defined in $frd:$undefined"; fail=1
fi

# 3. section references inside ble-protocol.md
ble=design/ble-protocol.md
if [ -f "$ble" ]; then
  missing=""
  for s in $(grep -oE '\(?[Ss]ections? [0-9]+(\.[0-9]+)?' "$ble" | grep -oE '[0-9]+(\.[0-9]+)?' | sort -u); do
    grep -qE "^#+ $s(\.| )" "$ble" || missing="$missing $s"
  done
  if [ -n "$missing" ]; then
    say "FAIL: $ble refers to sections that have no heading:$missing"; fail=1
  fi
fi

# 4. dependency versions
frd_ver=$(grep -oE '^\| Version \| [0-9]+\.[0-9]+' "$frd" | head -1 | grep -oE '[0-9]+\.[0-9]+')
for doc in design/*.md; do
  dep=$(grep -oE 'Depends on \| `design/functional-requirements.md` v[0-9]+\.[0-9]+' "$doc" | grep -oE 'v[0-9]+\.[0-9]+' | head -1)
  if [ -n "$dep" ] && [ "$dep" != "v$frd_ver" ]; then
    say "FAIL: $doc depends on FRD $dep but the FRD is at v$frd_ver"; fail=1
  fi
done

if [ "$fail" -eq 0 ]; then
  say "PASS: $(grep -cE '^\*\*N?FR-[A-Z]+-[0-9]+\*\*' "$frd") requirements defined, all citations resolve, FRD v$frd_ver"
fi
exit "$fail"
