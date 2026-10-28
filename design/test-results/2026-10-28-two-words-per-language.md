# The two words in each phone's language – 2026-10-28

The two words of a campfire from the lists of each phone's app language (BLE protocol v0.48 section 6.6, guiDesign v0.60 D-36, assessment SP-34), on the debug build of the working tree. **Result: pass. A German leader and a Dutch singer show the same pair translated; the release build has the English and Dutch lists only.**

| Check | How | Result |
|---|---|---|
| The lists | Written for English and German index by index from the Dutch lists of appendix A; checked by script and by `scripts/check-translation.py all` | **Pass.** Six lists of 256 words; no word twice in a list or in both lists of a language; Dutch 3 to 8 letters, English and German 3 to 10; the Dutch arrays equal appendix A word for word. Spot check: word 0 *aap* / *monkey* / *Affe*, list 2 word 0 *vuur* / *fire* / *Feuer*, *appel-vuur* / *apple-fire* / *Apfel-Feuer* |
| The check catches list mistakes | `scripts/test-check-translation.py`, five new cases: a word twice, a word in both lists, a list one short, two words in one item, Dutch differing from appendix A | **Pass**, all 20 cases. The last case first passed unnoticed because a missing appendix only warned; it is now an error |
| Build | `./gradlew check assembleDebug assembleRelease lintFieldtest` | **Pass**, unit tests of `core/protocol` (the fingerprint key, unsigned bytes, a list of the wrong length refused) and of the app (another campfire named by its words) included |
| German only in the review build | `aapt2 dump strings` on both APKs | **Pass.** *Affe* is in the debug APK only; *monkey* and the Dutch lists are in both |
| Two phones, two languages | A52 (Android 14), debug build set to German (`cmd locale set-app-locales … --locales de`), starts *Lagerfeuer 28 Okt.*; Huawei (Android 10), debug build in Dutch, on its home screen | **Pass.** The leader's bar shows *Kalb-Harfe*; the Huawei lists the campfire as *kalf-harp · Dichtbij*: word 36 of list 1 and word 86 of list 2 in each language. The campfire was ended and the A52 set back to automatic |

**Not tested:** a word longer than 8 letters on the join screen (the words come from a random key, so a long one cannot be chosen; the size rule is in `JoinQr.kt`); a language change in the middle of a campfire; English words on a phone. The beacon shows only the first 15 bytes of a name (*Lagerfeuer 28 O*), as before; the full name arrives after joining.
