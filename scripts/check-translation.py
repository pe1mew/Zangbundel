#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""
Checks the app's translations before a pull request (docs/TRANSLATING.md).

    python scripts/check-translation.py de          # one language
    python scripts/check-translation.py nl de       # several
    python scripts/check-translation.py all         # every language, plus the language lists
    python scripts/check-translation.py --strict de # warnings count as failures too

English (`values/`) is the base every other language is compared with; `en` checks the base on its own.
Errors are what the build or the app would reject, or what changes the meaning of the app (a lost
placeholder, a missing string, a link to another site). Warnings are for a person to look at: a term that
differs from the glossary, a text that may not fit, a text left in English. Exit status 1 on any error
(or warning with --strict), 2 on wrong usage. Only the Python standard library is used.
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES_SETS = ["main", "radiocheck", "debug", "fieldtest", "release"]
SRC = ROOT / "android" / "app" / "src"
GUI = ROOT / "design" / "guiDesign.md"

# CLDR plural categories Android uses per language; a language not listed needs at least "other".
PLURALS = {
    "en": {"one", "other"}, "nl": {"one", "other"}, "de": {"one", "other"}, "fr": {"one", "many", "other"},
    "es": {"one", "many", "other"}, "it": {"one", "many", "other"}, "pl": {"one", "few", "many", "other"},
    "da": {"one", "other"}, "sv": {"one", "other"}, "nb": {"one", "other"}, "fy": {"one", "other"},
}
# Header names of the glossary columns in guiDesign D-79, mapped to language codes.
COLUMN_LANGUAGE = {"dutch": "nl", "english": "en", "german": "de", "french": "fr", "frisian": "fy"}
# Texts that are the same in every language and need no warning when left as in English.
SAME_EVERYWHERE = {"Zangbundel", "A–Z", "Z–A", "Setlist", "Setlists", "Online", "Version %s", "Version %1$s, %2$s",
                   "Revision %1$s, %2$s", "Version %1$s (%2$d)", "max %1$d", "Bridge", "Radio check", "Name", "Start",
                   "Tags"}
LINK_PREFIX = "https://github.com/pe1mew/Zangbundel"
PLACEHOLDER = re.compile(r"%(?:(\d+)\$)?([sd%])")


class Report:
    def __init__(self, name):
        self.name, self.errors, self.warnings = name, [], []

    def error(self, where, text):
        self.errors.append(f"{where}: {text}")

    def warn(self, where, text):
        self.warnings.append(f"{where}: {text}")


def files_of(lang):
    """The strings files of one language, with the source set each lives in. `en` is the base `values/`."""
    folder = "values" if lang == "en" else f"values-{lang}"
    found = []
    for s in RES_SETS:
        d = SRC / s / "res" / folder
        if d.is_dir():
            found += [(s, p) for p in sorted(d.glob("strings*.xml"))]
    return found


def languages_present():
    langs = set()
    for s in RES_SETS:
        res = SRC / s / "res"
        if res.is_dir():
            for d in res.iterdir():
                m = re.fullmatch(r"values-([a-z]{2,3}(?:-r[A-Z]{2})?)", d.name)
                if m and list(d.glob("strings*.xml")):
                    langs.add(m.group(1))
    return sorted(langs)


def raw_text(el):
    """The text as written, entities decoded, Android's backslash escapes kept."""
    return "".join(el.itertext())


def load(lang, report):
    """Every string and plural of a language: {name: ("string", text, file) | ("plurals", {qty: text}, file)}."""
    entries, untranslatable = {}, set()
    for source_set, path in files_of(lang):
        rel = path.relative_to(ROOT).as_posix()
        data = path.read_bytes()
        if b"SPDX-License-Identifier: Apache-2.0" not in data[:300]:
            report.error(rel, "the file must start with the SPDX-License-Identifier: Apache-2.0 comment")
        try:
            root = ET.fromstring(data)
        except ET.ParseError as e:
            report.error(rel, f"not valid XML ({e}); the build cannot read it")
            continue
        for el in root:
            name = el.get("name")
            if el.tag not in ("string", "plurals", "string-array") or not name:
                continue
            if el.tag == "string-array":
                continue
            if name in entries:
                report.error(rel, f"'{name}' occurs twice")
            if el.get("translatable") == "false":
                untranslatable.add(name)
                if lang != "en":
                    report.error(rel, f"'{name}' is marked translatable=\"false\" in English and must not be translated")
                continue
            if list(el) and el.tag == "string":
                report.warn(rel, f"'{name}' contains markup; translations keep exactly the same tags")
            if el.tag == "string":
                entries[name] = ("string", raw_text(el), rel)
            else:
                entries[name] = ("plurals", {i.get("quantity"): raw_text(i) for i in el.findall("item")}, rel)
    return entries, untranslatable


def placeholders(text):
    return [(m.group(1) or "", m.group(2)) for m in PLACEHOLDER.finditer(text)]


def check_text(where, text, report):
    """Rules every text follows in every language (guiDesign D-78; what Android's resource compiler accepts)."""
    if not text.strip():
        report.error(where, "empty text")
        return
    if text.startswith(("@", "?")):
        report.error(where, "a text may not start with @ or ?: Android reads it as a reference; write \\@ or \\?")
    bare = re.sub(r"\\.", "", text)
    quoted = text.startswith('"') and text.endswith('"')
    if "'" in bare and not quoted:
        report.error(where, "apostrophe without backslash: write \\' (the build fails otherwise)")
    if '"' in bare and not quoted:
        report.error(where, 'straight double quote without backslash: write \\" or use typographic quotes')
    if text != text.strip():
        report.warn(where, "leading or trailing space")
    if "  " in text:
        report.warn(where, "two spaces in a row")
    if "..." in text:
        report.warn(where, "three dots: use the ellipsis character … (one character, as the other languages)")
    if "%" in PLACEHOLDER.sub("", text):
        report.error(where, "a % that is not a placeholder: write %% for a percent sign")


def compare_placeholders(where, base, target, report):
    b, t = placeholders(base), placeholders(target)
    if any(n for n, _ in b):
        if sorted(b) != sorted(t):
            report.error(where, f"placeholders {fmt(b)} in English, {fmt(t)} here: every one must stay, unchanged")
    elif b != t:
        if sorted(b) == sorted(t):
            report.error(where, f"placeholders {fmt(b)} in another order: unnumbered placeholders cannot move; "
                                "ask for numbered ones (%1$s) in an issue")
        else:
            report.error(where, f"placeholders {fmt(b)} in English, {fmt(t)} here: every one must stay, unchanged")


def fmt(ph):
    return " ".join("%" + (n + "$" if n else "") + k for n, k in ph) or "none"


def glossary():
    """guiDesign D-79's table: a list of {lang: [terms]} rows. Rows of short button words are left out."""
    try:
        text = GUI.read_text(encoding="utf-8")
    except OSError:
        return []
    start = text.find("**D-79")
    lines = text[start:].splitlines()
    head = next((i for i, l in enumerate(lines) if l.startswith("|") and "English" in l), None)
    if head is None:
        return []
    cols = [c.strip().split(" ")[0].lower() for c in lines[head].strip("|").split("|")]
    rows = []
    for l in lines[head + 2:]:
        if not l.startswith("|"):
            break
        cells = [c.strip() for c in l.strip("|").split("|")]
        row = {}
        for col, cell in zip(cols, cells):
            lang = COLUMN_LANGUAGE.get(col)
            if lang:
                cell = re.sub(r"\([^)]*\)", "", cell).replace("*", "")
                row[lang] = [t.strip() for t in cell.split(",") if t.strip()]
        if row.get("en") and not any(len(t) <= 5 for t in row["en"]):
            rows.append(row)
    return rows


def stem(term):
    t = term.lower()
    return t[:max(4, len(t) - 2)]


def check_language(lang, base, base_untranslatable, terms):
    report = Report(lang)
    if lang != "en" and not files_of(lang):
        report.error(lang, "no strings file found (expected android/app/src/<set>/res/values-" + lang + "/strings.xml)")
        return report
    entries, _ = (base, base_untranslatable) if lang == "en" else load(lang, report)
    if lang == "en":
        for name, (kind, value, rel) in base.items():
            for q, t in (value.items() if kind == "plurals" else [(None, value)]):
                check_text(f"{rel} '{name}'" + (f" [{q}]" if q else ""), t, report)
        return report
    needed = PLURALS.get(lang, {"other"})
    for name, (kind, value, rel) in base.items():
        if name not in entries:
            report.error(lang, f"'{name}' is missing (English: {short(value if kind == 'string' else value.get('other', ''))})")
            continue
        tkind, tvalue, trel = entries[name]
        where = f"{trel} '{name}'"
        if tkind != kind:
            report.error(where, f"is a {tkind} here but a {kind} in English")
            continue
        if kind == "string":
            check_one(where, value, tvalue, lang, terms, report)
        else:
            missing = needed - set(tvalue)
            if missing:
                report.error(where, f"plural forms {sorted(missing)} are needed for '{lang}'")
            extra = set(tvalue) - needed - {"zero", "two", "few", "many"}
            if extra:
                report.error(where, f"unknown plural forms {sorted(extra)}")
            for q, t in tvalue.items():
                check_one(f"{where} [{q}]", value.get("other", ""), t, lang, terms, report, plural=True)
    for name, (_, _, trel) in entries.items():
        if name not in base:
            report.error(f"{trel} '{name}'", "not in English: remove it, or add it to English first")
    return report


def check_one(where, base, target, lang, terms, report, plural=False):
    check_text(where, target, report)
    if not plural:
        compare_placeholders(where, base, target, report)
    else:
        if {k for _, k in placeholders(target)} - {k for _, k in placeholders(base)}:
            report.error(where, f"placeholders {fmt(placeholders(target))} do not occur in English")
    if base.startswith("https://"):
        if not target.startswith(LINK_PREFIX):
            report.error(where, f"a link must stay on {LINK_PREFIX}… (assessment SP-32): {target}")
        return
    if base.count("\\n") != target.count("\\n"):
        report.warn(where, f"{base.count(chr(92) + 'n')} line breaks (\\n) in English, {target.count(chr(92) + 'n')} here")
    if target == base and target not in SAME_EVERYWHERE and re.search(r"[A-Za-z]{3}", target):
        report.warn(where, f"same as English: \"{short(target)}\"; translate it, or confirm it is right as it is")
    if 6 <= len(base) <= 30 and len(target) > 2 * len(base) + 4:
        report.warn(where, f"{len(target)} characters for {len(base)} in English: a short label may not fit its button or field")
    for row in terms:
        en_hit = any(re.search(r"\b" + re.escape(t.lower()) + r"(?:s|'s)?\b", base.lower()) for t in row["en"])
        if en_hit and row.get(lang) and not any(stem(t) in target.lower() for t in row[lang]):
            report.warn(where, f"English uses '{'/'.join(row['en'])}'; the glossary term is '{'/'.join(row[lang])}' (guiDesign D-79)")


def short(text, n=60):
    return text if len(text) <= n else text[:n] + "…"


def read_list(path, pattern):
    try:
        return re.findall(pattern, path.read_text(encoding="utf-8"))
    except OSError:
        return None


def check_lists(langs):
    """The languages a build offers (languages.xml, locales_config.xml) match the strings files of that build."""
    report = Report("language lists")
    shipped = {"en"} | {l for l in langs if any(s in ("main", "radiocheck") for s, _ in files_of(l))}
    review = {l for l in langs if any(s == "debug" for s, _ in files_of(l))} - shipped
    for build, expected in (("main", shipped), ("debug", shipped | review)):
        res = SRC / build / "res"
        for rel, pattern in (("values/languages.xml", r"<item>([^<]+)</item>"),
                             ("xml/locales_config.xml", r'<locale android:name="([^"]+)"')):
            found = read_list(res / rel, pattern)
            if found is None:
                if build == "main" or review:
                    report.error(f"android/app/src/{build}/res/{rel}", "missing")
                continue
            if set(found) != expected:
                report.error(f"android/app/src/{build}/res/{rel}",
                             f"lists {sorted(set(found))}, the {build} build has strings for {sorted(expected)}")
    return report


def main(argv):
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    strict = "--strict" in argv
    args = [a for a in argv if a != "--strict"]
    if any(a.startswith("-") for a in args):
        print(__doc__)
        return 2
    present = languages_present()
    wanted = (["en"] + present) if (not args or args == ["all"]) else args
    base_report = Report("en")
    base, base_untranslatable = load("en", base_report)
    terms = glossary()
    reports = [base_report] if "en" not in wanted else []
    for lang in wanted:
        r = check_language(lang, base, base_untranslatable, terms)
        if lang == "en":
            r.errors = base_report.errors + r.errors
            r.warnings = base_report.warnings + r.warnings
        reports.append(r)
    if not args or args == ["all"]:
        reports.append(check_lists(present))
    failed = False
    for r in reports:
        if not r.errors and not r.warnings and r.name == "en" and "en" not in wanted:
            continue
        status = "OK" if not r.errors and not r.warnings else f"{len(r.errors)} error(s), {len(r.warnings)} warning(s)"
        print(f"== {r.name}: {status}")
        for e in r.errors:
            print(f"  ERROR    {e}")
        for w in r.warnings:
            print(f"  warning  {w}")
        failed |= bool(r.errors) or (strict and bool(r.warnings))
    if not terms:
        print("note: the glossary of guiDesign D-79 was not found; term checks skipped")
    print("FAIL" if failed else "PASS")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
