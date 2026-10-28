# Language setting and German under review – 2026-10-28

*Taal* in settings (FR-LANG-03, guiDesign D-76, architecture A-16) and German in the debug build only (FR-LANG-08, D-80), on the debug build of the working tree. **Result: pass on both phones; the setting works and is kept on Android 14 and Android 10, and German is absent from the release build.**

| Check | How | Result |
|---|---|---|
| Build | `./gradlew check assembleDebug assembleRelease lintFieldtest` | **Pass.** Lint's missing-translation check first failed on the radio check's strings, which the debug build also has (`src/radiocheck`); German for them was added in the debug source set, then everything passed |
| German only in debug | `aapt2 dump strings` on both APKs | **Pass.** The debug APK holds the German strings; the release APK holds none of them and offers *Nederlands* and *English* only. Its `de` resource configuration comes from AndroidX's own strings |
| Choice is kept on Android 8–12 | `aapt2 dump xmltree` on the release manifest | AppCompat's `AppLocalesMetadataHolderService` with `autoStoreLocales` is declared |
| A52, Android 14, phone in `nl-NL` | Settings: *Taal* shows *Automatisch (taal van de telefoon)*, *Nederlands*, *English*, *Deutsch*; tap *Deutsch* | **Pass.** The screen turns German at once (*Einstellungen*, *Sprache*); `cmd locale get-app-locales` reports `[de]` |
| | Tap *English*, force-stop, start again | **Pass.** The home screen is English (*Looking for campfires…*); the platform reports `[en]` |
| | Tap *Automatic (phone language)* | **Pass.** Back to Dutch; the platform reports `[]` |
| Huawei, Android 10, phone in `nl-GB` | Fresh install: the first-use introduction | **Pass.** Dutch, from the phone's language |
| | Settings → *Deutsch*, force-stop (process gone), start again | **Pass.** The home screen is German (*Suche nach Lagerfeuern…*, *Liederbuch*, *Funkprüfung*): AppCompat kept the choice. Set back to *Automatisch*: Dutch |
| Layout | The four choices on the A52 | The text touched the round buttons; a 12 dp gap added and checked on the A52 |
| Pull-down (the owner's request, same day) | The four round buttons replaced by one pull-down field, so more languages do not lengthen the screen; on the A52: the field, its open menu, and *Deutsch* chosen from it | **Pass.** The menu lists *Automatisch (taal van de telefoon)*, *Nederlands*, *English*, *Deutsch*; choosing *Deutsch* turns the screen German at once and the platform reports `[de]`. The closed field first cut off *Automatisch (taal van de te…*; it now wraps to two lines |
| Settings layout (the owner's request, same day) | On the A52 | The buttons *Statistieken van kampvuren* and *Alle gegevens verwijderen* stand on the backdrop without panel or heading, as on *Over*; the note under the name field has an 8 dp gap. Checked by screenshot |

**Found on the way:** on the Huawei, the home screen asked for the location permission, which Android 10 needs for Bluetooth scanning. That dialog is the owner's to answer and was left alone.

**Not tested:** a phone set to a language the app lacks (the English fallback, the platform's rule); Android 13's own per-app language screen in the system settings (the list comes from `locales_config.xml`, which `aapt2` shows); every German screen. Reading the German for correctness is the reviewer's task (D-80).
