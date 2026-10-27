# Store listing – Nederlands (nl-NL, default language)

Limits: app name ≤ 30, short description ≤ 80, full description ≤ 4000 characters. Plain text; Play ignores Markdown. Describes phase 1 only (nothing about karaoke or chords until they ship). F-Droid reads a copy in `fastlane/metadata/android/nl-NL/`: edit here, then run `bash scripts/store-metadata.sh`; each release note is headed `### <versionName> (<versionCode>)`.

## App name (≤ 30)

Zangbundel

## Short description (≤ 80)

Samen zingen rond het kampvuur: liedteksten via Bluetooth, zonder internet.

## Full description (≤ 4000)

Zangbundel is de liedbundel-app voor rond het kampvuur, bijvoorbeeld bij scouting.

Eén telefoon is de leider. De leider start een kampvuur en kiest de liederen; de app stuurt de liedtekst via Bluetooth naar alle telefoons rond het vuur, tegelijk en zonder internet, wifi of hotspot. Iedereen ziet dezelfde tekst op het moment dat het lied begint.

Zo werkt het
• De leider start een kampvuur en laat een QR-code zien, of zangers kiezen het kampvuur uit de lijst.
• Zangers scannen de code en doen mee. Geen account, geen registratie.
• De leider kiest een lied; de tekst verschijnt bij iedereen.
• Zangers scrollen regel voor regel mee, groot en duidelijk, overdag zwart op wit en 's avonds wit op zwart; wie wil, laat de leider het scrollen doen.
• De leider ziet hoeveel zangers er meedoen en of de verbinding goed is.
• Wie wil, stapt uit. De leider sluit het kampvuur af; de liedteksten verdwijnen weer van de telefoons en de lijst met gezongen titels blijft als herinnering.

Liedbundels
De app bevat zelf geen liedteksten. Liederen komen uit bundels die een groep of vereniging beheert, met de bijbehorende licentie-informatie. Bundels download je als je internet hebt, bijvoorbeeld thuis voor het kamp; daarna heb je geen internet meer nodig. Wie de bundel niet heeft, krijgt de tekst van de leider via Bluetooth.

Privacy
Zangbundel verzamelt niets en stuurt niets naar de maker van de app. Geen account, geen advertenties, geen analytics. Je weergavenaam wordt versleuteld naar de telefoon van de leider gestuurd; liedteksten worden onversleuteld uitgezonden omdat ze niets persoonlijks bevatten. Alles wat je tijdens een kampvuur ontvangt wordt verwijderd als het kampvuur eindigt.

Machtigingen
• Bluetooth: om kampvuren te vinden, eraan deel te nemen en als leider uit te zenden.
• Op Android 11 en ouder vraagt Android de locatiemachtiging om naar Bluetooth-apparaten te kunnen zoeken. Zangbundel leest je locatie nooit.
• Internet: alleen om liedbundels en setlists te downloaden als je dat bevestigt, of, als je dat aanzet, om via wifi te controleren op een nieuwe versie van een bundel.
• Meldingen: alleen de leider krijgt de vraag, voor een melding zolang het kampvuur loopt.

De app doorgeven kan ook zonder internet: een Android-telefoon in de buurt krijgt hem via Quick Share of Bluetooth.

Talen: Nederlands en Engels; liedbundels kunnen in elke taal zijn.

Zangbundel is open source: https://github.com/pe1mew/Zangbundel

## Release notes (≤ 500)

### 0.1.1 (2026102701)

Verbeterd: na het beëindigen van een kampvuur vindt het startscherm weer andere kampvuren. Nieuw: tik op de twee woorden van een kampvuur om te lezen wat ze betekenen. Over heeft een link naar de broncode en een knop naar de handleiding. De QR-code van de leider opent de app nu direct vanuit de camera.

### 0.1.0 (2026102201)

Eerste versie: kampvuur starten of meedoen via QR-code, liedteksten via Bluetooth, dag- en nachtweergave, liedbundels importeren, de app doorgeven zonder internet.
