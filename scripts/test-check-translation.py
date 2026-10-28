#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""
Tests scripts/check-translation.py: in a scratch copy of the app's resources it makes one mistake at a time
in the Dutch texts and checks that the check reports it, as an error (exit 1) or, for what a person must
judge, as a warning (exit 0). The unchanged copy must pass. The mistakes are made on whatever strings the
files hold now, so the test keeps working when the texts change.

    python scripts/test-check-translation.py

Exit status 0 when every mistake is caught. Only the Python standard library is used.
"""
import importlib.util
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CHECK = ROOT / "scripts" / "check-translation.py"
LANG = "nl"
NL = "android/app/src/main/res/values-nl/strings.xml"
EN = "android/app/src/main/res/values/strings.xml"
LIST = "android/app/src/main/res/values/languages.xml"
WORDS = "android/app/src/main/res/values-nl/strings_words.xml"
ITEM = re.compile(r"<item>([^<]*)</item>")


def word(t, i):
    """Word i of the two word lists counted together: 0 to 255 the first list, 256 to 511 the second."""
    return ITEM.findall(t)[i]


def swap_word(t, i, new):
    m = list(ITEM.finditer(t))[i]
    return t[:m.start(1)] + new + t[m.end(1):]
STRING = re.compile(r'(<string name="([^"]+)"[^>]*>)(.*?)(</string>)', re.S)

sys.dont_write_bytecode = True  # no __pycache__ beside the scripts
spec = importlib.util.spec_from_file_location("check_translation", CHECK)
check = importlib.util.module_from_spec(spec)
spec.loader.exec_module(check)


def texts(t):
    return {m.group(2): m for m in STRING.finditer(t)}


def first_plain(t):
    """The first string without placeholders, line breaks or links: a safe place to add a mistake."""
    return next(m for m in STRING.finditer(t) if "%" not in m.group(3) and "\\n" not in m.group(3)
                and "://" not in m.group(3) and len(m.group(3)) > 3)


def append(t, extra):
    m = first_plain(t)
    return t[:m.end(3)] + extra + t[m.end(3):]


def drop_placeholder(t):
    m = next(m for m in STRING.finditer(t) if check.PLACEHOLDER.search(m.group(3).replace("%%", "")))
    p = next(p for p in check.PLACEHOLDER.finditer(m.group(3)) if p.group(2) != "%")
    return t[:m.start(3) + p.start()] + t[m.start(3) + p.end():]


def set_text(t, name, value):
    m = texts(t)[name]
    return t[:m.start(3)] + value + t[m.end(3):]


def link_away(t):
    m = next(m for m in STRING.finditer(t) if m.group(3).startswith("https://"))
    return t[:m.start(3)] + "https://example.com/manual" + t[m.end(3):]


def untranslated(t, en):
    en_texts = texts(en)
    m = next(m for m in STRING.finditer(t) if m.group(2) in en_texts and m.group(3) != en_texts[m.group(2)].group(3)
             and re.fullmatch(r"[A-Za-z ]{4,}", en_texts[m.group(2)].group(3)))
    return set_text(t, m.group(2), en_texts[m.group(2)].group(3))


def off_glossary(t, en):
    en_texts = texts(en)
    for row in check.glossary():
        terms = row.get(LANG) or []
        for m in STRING.finditer(t):
            e = en_texts.get(m.group(2))
            if not e or not any(re.search(r"\b" + re.escape(x.lower()) + r"(?:s|'s)?\b", e.group(3).lower()) for x in row["en"]):
                continue
            stems = [check.stem(x) for x in terms if check.stem(x) in m.group(3).lower()]
            if stems:
                new = re.sub(re.escape(stems[0]) + r"\w*", "qqq", m.group(3), flags=re.I)
                return t[:m.start(3)] + new + t[m.end(3):]
    raise AssertionError("no string uses a glossary term")


CASES = [
    # (name, file, change, text the report must contain, a warning only)
    ("unchanged copy", None, None, "PASS", True),
    ("missing string", NL, lambda t, en: t.replace(first_plain(t).group(0), "", 1), "is missing", False),
    ("lost placeholder", NL, lambda t, en: drop_placeholder(t), "placeholders", False),
    ("unnumbered placeholders reordered", "pair", None, "another order", False),
    ("apostrophe", NL, lambda t, en: append(t, " x'y"), "apostrophe", False),
    ("straight double quote", NL, lambda t, en: append(t, ' "q"'), "double quote", False),
    ("stray percent sign", NL, lambda t, en: append(t, " 5 %"), "not a placeholder", False),
    ("plural form missing", NL, lambda t, en: re.sub(r'\s*<item quantity="one">.*?</item>', "", t, count=1), "plural forms", False),
    ("string not in English", NL, lambda t, en: t.replace("</resources>", '    <string name="made_up">X</string>\n</resources>'), "not in English", False),
    ("broken XML", NL, lambda t, en: append(t, " &"), "not valid XML", False),
    ("link to another site", NL, lambda t, en: link_away(t), "link must stay", False),
    ("file header missing", NL, lambda t, en: t.replace("<!-- SPDX-License-Identifier: Apache-2.0 -->", "", 1), "SPDX", False),
    ("left in English", NL, untranslated, "same as English", True),
    ("glossary term not used", NL, off_glossary, "glossary term", True),
    ("language list out of step", LIST, lambda t, en: t.replace(f"<item>{LANG}</item>", "", 1), "lists", False),
    ("word twice in a word list", WORDS, lambda t, en: swap_word(t, 1, word(t, 0)), "occurs twice", False),
    ("word in both word lists", WORDS, lambda t, en: swap_word(t, 256, word(t, 0)), "in both word lists", False),
    ("word list one short", WORDS, lambda t, en: re.sub(r"\s*<item>[^<]*</item>", "", t, count=1), "exactly 256", False),
    ("not a single word", WORDS, lambda t, en: swap_word(t, 2, "two words"), "one word of", False),
    ("Dutch words differ from the protocol", WORDS, lambda t, en: swap_word(t, 3, "zzzz"), "appendix A", False),
]


def copy_tree(work):
    for rel in ("scripts/check-translation.py", "design/guiDesign.md", "design/ble-protocol.md"):
        (work / rel).parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(ROOT / rel, work / rel)
    src = ROOT / "android" / "app" / "src"
    for res in src.glob("*/res"):
        shutil.copytree(res, work / res.relative_to(ROOT), ignore=shutil.ignore_patterns("*.png", "*.webp", "*.jpg"))


def main():
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    missed = 0
    for name, rel, change, expect, warning in CASES:
        with tempfile.TemporaryDirectory() as tmp:
            work = Path(tmp)
            copy_tree(work)
            en = (work / EN).read_text(encoding="utf-8")
            if rel == "pair":
                for path, text in ((EN, "%s and %d"), (NL, "%d en %s")):
                    p = work / path
                    p.write_text(p.read_text(encoding="utf-8").replace("</resources>", f'    <string name="pair">{text}</string>\n</resources>'), encoding="utf-8")
            elif rel:
                p = work / rel
                before = p.read_text(encoding="utf-8")
                after = change(before, en)
                if after == before:
                    print(f"MISS {name}: the mistake could not be made in the current texts")
                    missed += 1
                    continue
                p.write_text(after, encoding="utf-8")
            r = subprocess.run([sys.executable, "-I", str(work / "scripts/check-translation.py"), "all"],
                               capture_output=True, text=True, encoding="utf-8")
            exit_ok = r.returncode == 0 if warning else r.returncode == 1
            ok = expect in r.stdout and exit_ok
            missed += not ok
            print(f"{'ok  ' if ok else 'MISS'} {name}: exit {r.returncode}")
            if not ok:
                print(r.stdout[-1500:])
    print("PASS: every mistake caught" if not missed else f"FAIL: {missed} not caught")
    return 1 if missed else 0


if __name__ == "__main__":
    sys.exit(main())
