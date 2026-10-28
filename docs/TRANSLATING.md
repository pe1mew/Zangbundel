# Translating and reviewing Zangbundel

For people who review or improve the app's texts in Dutch, English or German, and later in another language. It says what to review, which rules are mandatory and why, how to check your work before you send it, and how to send it as a pull request. Read it once in full before your first review; afterwards sections 4 and 7 are the checklist.

## 1. The languages today

| Language | Code | Status | Where its texts are |
|---|---|---|---|
| Dutch (*Nederlands*) | `nl` | Primary: every text is written in Dutch first. In every build. | `android/app/src/main/res/values-nl/` |
| English | `en` | The fallback: a phone in a language the app lacks shows English. In every build. | `android/app/src/main/res/values/` |
| German (*Deutsch*) | `de` | **Under review.** A draft, in the review build only (section 6), not in the Play or F-Droid build. It ships after a fluent speaker has approved it. The draft was translated mainly from the English: compare it with the Dutch, which is the source. | `android/app/src/debug/res/values-de/` |

Each language has three files: `strings.xml` with the app's texts, `strings_words.xml` with the two word lists of a campfire's two words (rule 10 of section 4), and `strings_radiocheck.xml` with the texts of the radio check and the field-test consent, which exist only in development and field-test builds. For Dutch and English the last file is in `android/app/src/radiocheck/res/`.

The rules behind this are requirements FR-LANG-01 to FR-LANG-08 in `design/functional-requirements.md` and decisions D-75 to D-80 in `design/guiDesign.md` section 8.

## 2. What a review covers, and what it does not

**In scope:**

- Every text in the files of your language: buttons, titles, messages, the import report, the privacy screen, the explanation of the two words, the consent of the field test, and the 512 words of the two word lists.
- The fixed terms of your language in the glossary, the table of D-79 in `design/guiDesign.md`. For German that column is a draft and part of the review.
- Whether each text reads well **in the app**, on a phone (section 6), not only in the file.

**Out of scope:**

- **Song lyrics.** The app never translates a song (D-75). A song in another language is a separate file in a song library, not here.
- **Library names and descriptions.** They are shown as their maintainer wrote them (D-77).
- **The meaning of a text.** A review makes the translation say what the Dutch says, well. If you think the Dutch itself is wrong or unclear, open an issue; do not change the meaning in your language alone. This matters most for the privacy texts (section 4.6).

Texts outside the app that a language needs before it ships (D-80): the privacy policy (`docs/privacy-policy.md`, mandatory for a new language), and later the store listing (`docs/store-listing/`), the join page (`docs/j/index.html`) and the manual on the wiki (`wiki/`). These are separate pull requests.

## 3. How the files work, and why

The files are Android string resources: XML that the build turns into the app. A text looks like this:

```xml
<string name="library_installed">%s hinzugefügt</string>
```

- **`name`** is the key the app's code uses to find the text. It is the same in every language and never changes; only the text between the tags is yours.
- **`%s`, `%d`, `%1$s`, `%2$d`** are **placeholders**: the app puts something there at run time, a name, a number, a version. `%s` is text, `%d` a whole number. A number with a dollar sign (`%1$s`) says which value it is, so a translation can put the values in a different order. A placeholder that goes missing, or one that is added, makes the app show the wrong thing or crash.
- **Plurals** are written as one entry with one item per grammatical form, because languages count differently. Dutch, English and German need the forms `one` and `other`; other languages need more (Polish: `one`, `few`, `many`, `other`). The phone picks the form by the number.

  ```xml
  <plurals name="library_songs">
      <item quantity="one">%d Lied</item>
      <item quantity="other">%d Lieder</item>
  </plurals>
  ```

- **Escapes:** an apostrophe is written `\'`, a straight double quote `\"`, a line break `\n`, a percent sign that is not a placeholder `%%`. A text may not begin with `@` or `?`. These are rules of the Android build, which fails or drops characters otherwise.
- **`translatable="false"`** in the English file marks a text that is the same everywhere, such as the address of the source code. It does not appear in other languages.
- **Comments** (`<!-- … -->`) say where a text appears, with the number of the design decision. They help you find the screen; translating them is not needed.

**Why this format.** One file per language means a language is added or improved without touching the app's code (FR-LANG-02), and one person who speaks it can review exactly that file (D-80). The build compares every language with English: a missing text or a text that is not in English stops the build (D-78). Placeholders and plural forms are the platform's own way to let a sentence be translated whole, instead of being glued together from pieces, which no translation can get right (D-78). Dutch is the source because the app is made for Dutch scouting groups; English is the fallback because it is the language a German, French or Polish phone in a Dutch camp most likely understands (D-76).

## 4. Mandatory rules

These are not style preferences: breaking one makes the build fail, the app misbehave, or the app say something untrue. `scripts/check-translation.py` checks those it can (section 7).

1. **Change only the text.** Never change a `name`, never add or remove an entry, never rename or move a file. Only the English file gets new entries, by the maintainer, together with every other language (D-78).
2. **Keep every placeholder.** The same placeholders as in English, each exactly once. Numbered ones may move within the sentence, but `%1$d` must stay next to the word it belongs to: in `Importiert: %1$d · Übersprungen: %2$d` the first number is the imported songs in every language. Unnumbered ones (`%s`, `%d`) must stay in the same order; if your language needs them in another order, open an issue so the maintainer numbers them.
3. **Give every plural form** your language needs, each with the same placeholder as in English.
4. **Escape** apostrophes (`\'`), straight double quotes (`\"`) and percent signs (`%%`). Typographic quotes („…“, “…”, ‘…’) need no escape and are preferred in German and Dutch.
5. **Use the glossary.** The fixed terms of D-79 (*kampvuur* / campfire / *Lagerfeuer*, *leider* / leader / *Leiter*, and so on) are used for that thing everywhere, in the app, the store listing and the privacy texts, so a child never wonders whether *Lagerfeuer* and *Feuer* are two different things. If your language needs another term, propose it in your pull request or issue for the table; a new term goes into the table before it is used in a text. **Gender forms in German** (*Leiter* or *Leiterin/Leiter*, *Sänger* or *Sänger\*innen*, or a neutral word such as *Leitung*) are not decided yet: the draft uses the masculine forms as placeholders, and the reviewers decide, in a pull request or an issue. The choice then goes into the German column of the table and is used everywhere.
6. **Privacy texts say exactly what the Dutch says, no more and no less.** The users are under 18, and these texts are a promise: what the app sends, what it keeps, what it never does. The strings beginning with `privacy_`, `statistics_`, `words_` and `fieldtest_consent_`, and `home_bluetooth_why`, must be checked sentence by sentence against the Dutch. They must be readable by a 12-year-old (FR-GEN-08). The explanation of the two words (`words_text`) must not promise more protection than there is: the words are a help, not a lock.
7. **The notification text stays neutral.** `notification_gathering_running` (*Kampvuur actief*, *Lagerfeuer läuft*) shows on the lock screen; it never names the campfire or a person (assessment SP-17).
8. **Links stay on the project's GitHub.** The one translatable link, `about_manual_url`, points to the manual in your language on `https://github.com/pe1mew/Zangbundel/wiki/…`, or to the English manual while yours does not exist. Never to another site (assessment SP-32).
9. **Address the user informally and kindly.** Dutch *je*, German *du*; short sentences; no blame (*Dat lukte niet* rather than *Fout*); the same tone as the Dutch.
10. **The two words are translated word by word.** The two words of a campfire (*appel-vuur*) come from two lists of 256 words in `strings_words.xml` of each language (`fingerprint_words_1`, `fingerprint_words_2`). Word *n* of your list means what Dutch word *n* means, because a Dutch leader's phone and a German singer's phone pick the words by the same number: *appel-vuur* must become *Apfel-Feuer*, not two other words. So never reorder, add or remove words. Each word is one word of 3 to 10 letters, concrete and known to children, not rude or slang; no word may appear twice in a list or in both lists, and no two words in a list may sound alike (*Hummel*, *Hummer*). When the plain translation would break one of these rules, choose another word of the same kind (another animal, another food) and say so in your pull request. The Dutch lists are fixed by BLE protocol appendix A and change only with it. The check of section 7 tests all of this except meaning and sound.
11. **Keep the file header.** Each file starts with the line `<!-- SPDX-License-Identifier: Apache-2.0 -->`; files are UTF-8 with Unix line endings.

## 5. Limitations to know about

- **Space.** Buttons and fields are sized for the Dutch, which is usually the longest; a much longer text may wrap or be cut off (D-36, D-78). If a short label grows a lot, look at it on the phone. The check warns when a short text more than doubles.
- **No right-to-left languages** for now (FRD Q8), and no regional variants: a phone set to Flemish (`nl-BE`) or Austrian German (`de-AT`) gets Dutch or German.
- **Dates and numbers** are formatted by the phone's language; you do not write them.
- **The app's name stays *Zangbundel*** in every language, and the names of the languages in the *Taal* menu (*Nederlands*, *English*, *Deutsch*) come from the phone, each in its own language.
- **Generated section labels** (*Couplet* / *Verse* / *Strophe*, *Refrein* / *Chorus* / *Refrain*) follow the app's language, also above a song in another language (D-77).
- **German is in the review build only** until it is approved. Its manual button opens the English manual, labelled *Anleitung (Englisch)*, until a German manual exists.

## 6. Reviewing on a phone

A text is only right when it fits its screen. You need an Android phone (Android 8 or later) and the **review build**: the app with every language under review built in. Every build on GitHub makes one.

1. Sign in to GitHub, open **Actions** in the repository (`https://github.com/pe1mew/Zangbundel/actions`), choose the workflow **android**, and open its newest run on `main`, marked green.
2. Under **Artifacts**, download **zangbundel-review-build**. It is a zip file holding `app-debug.apk`; review builds are kept for 30 days.
3. Unzip it on the phone, or copy the APK to the phone, and open it. Android asks to allow installing from this source: allow it for the app that opens the file, install, and switch it off again afterwards.
4. The review build installs **next to** the store app, not over it: there are then two *Zangbundel* icons. It is signed with a throwaway development key, so its fingerprint on *Over* differs from the store app's; that is expected. Remove it when you are done.
5. Open it, and choose your language under **Instellingen → Taal** (*Settings → Language*, *Einstellungen → Sprache*).
6. Go through the screens, and compare what you see with the file:
   - first use: *Zo werkt het*, and the privacy screen behind *Wat gebeurt er met mijn gegevens?*;
   - home, the songbook, a song, the library screen and *Bibliotheek toevoegen*, setlists;
   - settings, the statistics screen, *Over* and its install panel with *Delen zonder internet*;
   - with a second phone: starting a campfire, the leader's page and drawer, joining as a singer, the singer's toolbar and song info, the two words, ending and the summary, memories;
   - the radio check (*Radiocheck* on the home screen of the review build) and, in a field-test build, the consent.
7. Note each change with the string's `name` if you know it, or the screen and the text you saw, and send it as in section 8.

Building it yourself also works, with the Android SDK: `./gradlew assembleDebug` in `android/` (see `android/README.md`).

Screens that need two phones or a campfire are the hardest to reach; the strings for them are all in the file, with a comment that names the screen.

## 7. Checking your work

Before you send anything, run the check from the repository's root (Python 3.8 or later, nothing to install):

```bash
python scripts/check-translation.py de
```

```bash
python scripts/check-translation.py all
```

The first checks one language against English; the second checks every language and also that the language lists of each build (`languages.xml`, `locales_config.xml`) name exactly the languages that have texts in it.

- **ERROR** is something the build or the app would reject, or that changes what the app says: a missing or extra string, a lost placeholder, a missing plural form, an unescaped apostrophe or quote, broken XML, a link to another site, a missing file header. Fix every error; the check ends with `FAIL` and exit status 1 while one remains.
- **warning** is for you to look at: a text identical to English, a term that differs from the glossary, a short text that more than doubles, a change in line breaks. Fix it, or say in your pull request why it is right. `--strict` makes warnings fail as well.

The same check runs on every pull request (the *android* workflow), together with the Android build's own lint, which catches the rest. Whoever changes the check itself also runs `python scripts/test-check-translation.py`, which makes each kind of mistake on purpose in a scratch copy and confirms the check reports it; CI runs it too.

## 8. Sending your review: an issue or a pull request

Feedback comes in one of two ways; both are welcome.

- **An issue**, if you do not edit files yourself: open a new issue in the repository and choose the form **Translation feedback**. Give the language, the screen, the text as it is and your proposal; several texts may go in one issue. For a language under review, the form has a box to approve it for release.
- **A pull request**, if you edit the files yourself, as below. The check of section 7 then runs on your changes.

You need a GitHub account for both. The commands use Git on the command line; GitHub Desktop or the web editor work as well.

1. **Fork** `https://github.com/pe1mew/Zangbundel` with the *Fork* button.
2. **Clone your fork** and make a branch named after the language:

   ```bash
   git clone https://github.com/<your-user>/Zangbundel.git
   ```

   ```bash
   cd Zangbundel
   ```

   ```bash
   git checkout -b translation/de-review
   ```

3. **Edit** the files of your language only (and, for a glossary term, the table of D-79 in `design/guiDesign.md`). Keep to section 4.
4. **Check:** `python scripts/check-translation.py all` must end with `PASS`.
5. **Commit** with a message in the imperative that names the language, for example:

   ```bash
   git commit -am "German: review of the singer's screens and the privacy texts"
   ```

6. **Push** and open a pull request against `main` of `pe1mew/Zangbundel`:

   ```bash
   git push origin translation/de-review
   ```

7. **In the pull request**, write:
   - which language, and which parts you reviewed (all, or which screens);
   - whether you reviewed on a phone, which one, and which screens you could not reach;
   - the result of the check (`PASS`, and any warnings you left with the reason);
   - every glossary term you propose to change;
   - for a language under review: whether you approve it for release, or what is still missing.

The maintainer reviews the pull request, may ask questions, and merges it. Keep your branch up to date with `main` until then. Small pull requests (one screen group, or the privacy texts) are easier to review than one large one; several are welcome.

**When a language under review is approved,** the maintainer moves its files from `src/debug` to `src/main`, adds it to the language lists of the main build, and adds the privacy policy in that language (D-80). From the next release on it is in the Play and F-Droid builds. You do not need to do this yourself.

## 9. Proposing a new language

Open an issue first: a new language is the owner's decision (FR-LANG-08). Once agreed, it starts as German did: the two files in `android/app/src/debug/res/values-<code>/`, the code added to `android/app/src/debug/res/values/languages.xml` and `…/xml/locales_config.xml`, a column in the glossary of D-79, and, if the language counts differently, its plural forms in `PLURALS` at the top of `scripts/check-translation.py`. Right-to-left languages need a decision first (FRD Q8).

Questions: open an issue, or write to zangbundel@rfsee.nl. Discussion in Dutch, English or German is welcome.
