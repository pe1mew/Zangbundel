# Store listing – German (de-DE)

Limits: app name ≤ 30, short description ≤ 80, full description ≤ 4000 characters. Plain text; Play ignores Markdown. Describes phase 1 only. Translated from `en-US.md` on 2026-11-06 with the German terms of guiDesign D-79 and the app's gender star (*Leiter\*in*, *Sänger\*innen*); like the app's German texts it is checked by the German reviewer (the owner's decision 2026-11-06). F-Droid reads a copy in `fastlane/metadata/android/de-DE/`: edit here, then run `bash scripts/store-metadata.sh`; each release note is headed `### <versionName> (<versionCode>)`.

## App name (≤ 30)

Zangbundel

## Short description (≤ 80)

Gemeinsam am Lagerfeuer singen. Liedtexte über Bluetooth, ohne Internet.

## Full description (≤ 4000)

Zangbundel (niederländisch für „Liederbuch“) ist die Liederbuch-App für das Lagerfeuer, zum Beispiel bei Pfadfinderlagern.

Ein Telefon gehört der Leitung. Die*der Leiter*in startet ein Lagerfeuer und wählt die Lieder; die App schickt die Liedtexte über Bluetooth an jedes Telefon rund ums Feuer, im selben Moment und ohne Internet, WLAN oder Hotspot. Alle sehen dieselben Worte, wenn das Lied beginnt.

So funktioniert es
• Die*der Leiter*in startet ein Lagerfeuer und zeigt einen QR-Code, oder die Sänger*innen wählen das Lagerfeuer aus einer Liste.
• Die Sänger*innen scannen den Code und machen mit. Kein Konto, keine Anmeldung.
• Die*der Leiter*in wählt ein Lied; der Liedtext erscheint auf allen Telefonen.
• Sänger*innen scrollen Zeile für Zeile mit, groß und deutlich: schwarz auf weiß am Tag, weiß auf schwarz in der Nacht; oder sie lassen die Leitung scrollen.
• Die*der Leiter*in sieht, wie viele Sänger*innen mitmachen und ob die Verbindung gut ist.
• Alle können jederzeit gehen. Die*der Leiter*in beendet das Lagerfeuer; die Liedtexte verschwinden wieder von den Telefonen, und die Liste der gesungenen Lieder bleibt als Erinnerung.

Liederbibliotheken
Die App enthält selbst keine Liedtexte. Die Lieder kommen aus Bibliotheken, die eine Gruppe oder ein Verband pflegt, mit ihren Lizenzangaben. Eine Bibliothek lädst du herunter, wenn du Internet hast, zum Beispiel zu Hause vor dem Lager; danach ist kein Internet mehr nötig. Sänger*innen ohne die Bibliothek bekommen die Texte von der Leitung über Bluetooth.

Datenschutz
Zangbundel erhebt nichts und schickt nichts an den Entwickler. Kein Konto, keine Werbung, keine Analyse. Dein Anzeigename wird verschlüsselt an das Telefon der Leitung geschickt; Liedtexte werden unverschlüsselt gesendet, weil sie nichts Persönliches enthalten. Alles, was du während eines Lagerfeuers bekommst, wird gelöscht, wenn es endet.

Berechtigungen
• Bluetooth: um Lagerfeuer zu finden und mitzumachen, und um als Leiter*in zu senden.
• Unter Android 11 und älter verlangt Android die Standortberechtigung, um nach Bluetooth-Geräten zu suchen. Zangbundel liest nie deinen Standort.
• Internet: nur um Liederbibliotheken und Setlists herunterzuladen, wenn du es bestätigst, oder, wenn du es einschaltest, um im WLAN nach einer neuen Version einer Bibliothek zu suchen; und wenn du unter „Über“ auf „Nach einer neuen Version suchen“ tippst, um GitHub nach der neuesten Version der App zu fragen.
• Benachrichtigungen: nur die Leitung wird gefragt, für eine Benachrichtigung, solange das Lagerfeuer läuft.

Die App lässt sich auch ohne Internet weitergeben: ein Android-Telefon in der Nähe bekommt sie über Quick Share oder Bluetooth.

Sprachen: Niederländisch, Englisch und Deutsch; Liederbibliotheken können in jeder Sprache sein.

Zangbundel ist Open Source: https://github.com/pe1mew/Zangbundel

## Release notes (≤ 500)

### 0.5.1 (2026111201)

Behoben: Ein Lied, das seinen Refrain innerhalb seines eigenen Refrains wiederholt, konnte den Speicher des Telefons füllen und die App schließen, beim Hinzufügen einer Bibliothek oder wenn die Leitung dieses Lied an einem Lagerfeuer schickte. Ein solches Lied wird jetzt übersprungen; beim Hinzufügen einer Bibliothek sagt die App, warum. Alle anderen Lieder bleiben, wie sie waren. Auch eine beschädigte Bibliotheksdatei schließt die App nicht mehr; sie wird abgelehnt.

### 0.5.0 (2026111101)

Neu: Die Schlagwörter im Liederbuch zeigen die Namen, die eine Bibliothek ihnen gibt, in deiner Sprache, etwa „Lagerfeuer“ statt „kampvuur“. Bibliotheken aus dem Bibliotheksmanager werden nach denselben Regeln geprüft wie im Manager, sodass App und Manager sich einig sind, was an einer Bibliothek falsch ist.

### 0.4.0 (2026110601)

Neu: Bibliotheken aus dem Bibliotheksmanager, die signiert sein können. Die Liste zeigt, von wem eine Bibliothek signiert ist, oder dass sie nicht signiert ist. Eine Bibliothek in WhatsApp bekommen oder heruntergeladen? Wähle Teilen → Zangbundel. Unter „Über“ kannst du jetzt nach einer neuen Version der App suchen. In den Einstellungen schließt ein Häkchen die Eingabe deines Namens ab.

### 0.3.0 (2026110301)

Neu: Zangbundel spricht Deutsch. Wähle Deutsch unter Einstellungen → Sprache, oder lass die App der Sprache des Telefons folgen. Klarere Worte: Der Knopf der Leitung auf dem Startbildschirm heißt jetzt „Lagerfeuer leiten“, und das Namensfeld in den Einstellungen heißt „Dein Name“.
