# Handleiding Zangbundel

*In English: [Manual](Manual)*

Zangbundel is een zangbundel voor bij het kampvuur. Eén telefoon is de **kampvuurleider**: die kiest de liederen en stuurt de tekst via Bluetooth naar alle telefoons in de buurt. De anderen zijn **zangers**: zij zien de tekst die de leider stuurt. Tijdens het kampvuur is er geen internet nodig, geen wifi en geen koppelen van Bluetooth.

De app bevat zelf geen liedteksten. Liederen komen uit **bibliotheken** die de leider toevoegt; elke bibliotheek heeft een eigen licentie. Om mee te zingen heb je geen bibliotheek nodig: de leider stuurt elk lied.

Deze handleiding hoort bij versie 0.1.1.

---

## Inhoud

1. [Installeren](#1-installeren)
2. [De eerste keer](#2-de-eerste-keer)
3. [Liederen toevoegen](#3-liederen-toevoegen)
4. [De zangbundel](#4-de-zangbundel)
5. [Setlists](#5-setlists)
6. [Een kampvuur leiden](#6-een-kampvuur-leiden)
7. [Meezingen](#7-meezingen)
8. [Herinneringen](#8-herinneringen)
9. [Instellingen](#9-instellingen)
10. [Over en privacy](#10-over-en-privacy)
11. [Als het niet lukt](#11-als-het-niet-lukt)

---

## 1. Installeren

Zangbundel is een Android-app (Android 8 of nieuwer). Installeer hem via Google Play, het liefst thuis, vóór het kamp: installeren heeft internet nodig.

Heeft iemand de app nog niet? Op **Over → Zangbundel op een andere telefoon** staat een QR-code met de link naar de app; met **Link delen** stuur je de link door. Tijdens een kampvuur vindt de leider hetzelfde in de leiderbalk onder **App installeren**.

**Zonder internet** kan de app van telefoon naar telefoon: kies op dat scherm **Delen zonder internet** en volg de stappen. Je deelt het installatiebestand via Quick Share of Bluetooth. Een kopie die zo is gedeeld, is echt als de vingerafdruk onder **Over → Ondertekening** `57:86:7B:…:9B:10` is.

## 2. De eerste keer

Bij de eerste keer openen zie je **Zo werkt het**, met de knop **Wat gebeurt er met mijn gegevens?**. Tik op **Beginnen**.

Het startscherm vraagt dan om **Bluetooth toestaan**. Android vraagt toestemming om apparaten in de buurt te vinden; die heeft de app nodig om mee te zingen en om een kampvuur te starten. De app leest geen locatie. Op Android 11 en ouder vraagt Android wel om locatietoestemming, omdat zoeken naar Bluetooth-apparaten daar zo werkt. Staat Bluetooth uit, dan biedt de app **Bluetooth aanzetten** aan.

## 3. Liederen toevoegen

Alleen de leider heeft liederen nodig. Ga naar **Zangbundel → Bibliotheken**.

- **Zangbundel bibliotheek toevoegen**: de eigen bibliotheek van de app met kampvuur- en kinderliederen, gedownload van GitHub.
- **Bibliotheek toevoegen → Online**: typ het webadres van een bibliotheek, een release-index of een `.zbl`-bestand en tik op **Opzoeken**. De app laat de versie en grootte zien; tik op **Downloaden**.
- **Bibliotheek toevoegen → Uit een bestand**: een `.zbl`- of `.zip`-bestand dat al op de telefoon staat, handig op een kampterrein zonder internet. Tik op **Bestand openen**.

Daarna toont de app de **licentie** van de bibliotheek. Pas na **Accepteren** komen de liederen in de zangbundel. Liederen die de app niet kan lezen, worden overgeslagen; het rapport zegt welke en waarom.

Per bibliotheek kun je:

- de liederen **Aan** of **Uit** zetten in de zangbundel;
- **Bijwerken** naar de nieuwste versie; de app laat zien wat er veranderd is;
- **Automatisch controleren op wifi** aanzetten: één keer per dag, alleen op wifi, en er wordt niets gedownload tot je op **Bijwerken** tikt;
- **Verwijderen**.

Weigert de app een bibliotheek met *Deze bibliotheek vraagt een nieuwere versie van de app*, werk dan de app bij.

## 4. De zangbundel

**Zangbundel** op het startscherm toont alle liederen van de bibliotheken die aan staan.

- **Zoeken** zoekt op titel; met **Ook in de tekst** ook in de liedteksten.
- De knoppen onder het zoekveld filteren, bijvoorbeeld op taal of soort lied; **A–Z** wisselt de volgorde.
- Tik op een lied om het te lezen. Het **ⓘ** naast de titel, of lang drukken op de titel, toont de liedinfo: tekst en muziek, licentie, bron.
- Vanuit een lied voeg je het toe aan een setlist.

## 5. Setlists

Een setlist is de lijst liederen voor een avond. Ga naar **Zangbundel → Setlists**.

- **Nieuwe setlist** maakt een lege lijst; vul hem vanuit de zangbundel met **Toevoegen aan setlist…**.
- Sleep een lied aan het handvat om de volgorde te veranderen.
- **Naam wijzigen**, **Dupliceren** en **Verwijderen** werken op de hele lijst; de liederen blijven in de zangbundel.
- **Exporteren** deelt de setlist als `.zbs`-bestand; **Importeren** leest zo'n bestand of het webadres ervan.
- **Uit bibliotheken** toont setlists die in een bibliotheek zitten; die zijn alleen te lezen.

Staat een lied van de setlist niet in je bibliotheken, dan staat het als *niet gevonden* in de lijst; het vult zich aan zodra de bibliotheek is toegevoegd.

## 6. Een kampvuur leiden

### Voor je begint

Zet de **batterijbespaarstand uit** en neem een **powerbank** mee: een kampvuur duurt uren, en de bespaarstand kan het uitzenden stoppen. Het startscherm herinnert je daaraan.

### Starten

1. Tik op het startscherm op **Kampvuur starten**. De app luistert eerst 5 seconden (*Even luisteren…*) of er al een kampvuur in de buurt is. Is dat zo, dan zegt de app *Er brandt al een kampvuur*: zing daar mee, of ga buiten bereik.
2. Kies op **Kampvuur starten**:
   - **Naam van het kampvuur** (voorgesteld: *Kampvuur* met de datum);
   - **Wie mag meedoen**:
     - **Iedereen in de buurt**: zangers tikken op het kampvuur in hun lijst;
     - **Met een deelnamecode**: vier cijfers die je hardop zegt;
     - **Alleen via de QR-code**: zangers scannen de QR-code van je scherm; zo komt niemand anders binnen. Aan te raden voor jonge groepen;
   - **Setlist**, of **Geen setlist**.
3. Tik op **Starten**. De eerste keer legt de app uit dat er een melding komt zolang het kampvuur loopt, en vraagt Android of dat mag. Weigeren mag: het kampvuur werkt dan precies hetzelfde.

### Het scherm van de leider

Je ziet dezelfde tekst als de zangers. Bovenaan staat de setlist; elk lied begint met een balk met **Nu**.

- **Nu** stuurt dat lied naar alle zangers. Het lied krijgt een oranje streep en *wordt gezongen*; eerdere liederen staan als *gezongen*.
- **Wis** op het lied dat gezongen wordt: er wordt even niets gezongen. Zangers houden de tekst, met *gewist* in hun balk.
- Zonder setlist zie je **Kies een lied uit de zangbundel** met **Zangbundel openen**.
- De ronde knoppen rechtsonder (**▲** vorig lied, **≡** lijst, **▼** volgend lied) bewegen alleen jouw eigen scherm; ze sturen niets.

**≡** opent het blad met twee tabbladen:

- **Setlist**: het lied dat gezongen wordt en de gezongen liederen blijven staan; de volgende liederen verplaats je aan hun handvat of veeg je weg (even **Ongedaan maken**). Elke wijziging gaat meteen naar de zangers.
- **Zangbundel**: zoek op titel. **Toevoegen** zet het lied achteraan de setlist; **Nu zingen** zet het direct na het huidige lied en stuurt het meteen.

### De leiderbalk

De smalle balk bovenaan toont de naam, **de twee woorden**, het aantal zangers en de tijd. Tik erop (of trek hem omlaag) om hem te openen:

- **Tijd**, **Liederen**, **Aangemeld** (met het hoogste aantal) en **Bereik** (*goed*, *matig* of *zwak*);
- **De twee woorden van dit kampvuur**: groot, om hardop te zeggen. Zangers zien dezelfde woorden naast de naam van het kampvuur;
- **Deelnamecode** (als je die koos) en **QR-code tonen**: de QR-code groot op een wit vlak; tik om te sluiten;
- **App installeren**: de link naar de app voor wie hem nog niet heeft;
- **Statistieken delen**, als je statistieken bewaart (zie [Instellingen](#9-instellingen));
- een ⚠ in de balk als de **batterijbespaarstand** aan staat of er een **ander kampvuur in de buurt** is; de geopende balk zegt welke;
- **Kampvuur beëindigen**.

### Beëindigen

**Kampvuur beëindigen → Afsluiten**: de zangers zien dat het kampvuur is afgelopen en de liedteksten verdwijnen van hun telefoons. Jij ziet een overzicht (tijd, liederen, meeste zangers tegelijk) en tikt op **Terug naar start**.

### Als de app stopt

Stopt de app terwijl je leidt, open hem dan binnen 10 minuten opnieuw: je krijgt **Hervatten** (verder met dezelfde zangers en liederen) of **Opnieuw beginnen**.

## 7. Meezingen

### Meedoen

- **Uit de lijst**: open de app. Onder *Kampvuren in de buurt* staat elk kampvuur met zijn twee woorden en hoe dichtbij het is (*Dichtbij*, *Binnen bereik*, *Ver weg*). Tik erop. Staat er een slotje, typ dan de **deelnamecode** die de leider zegt.
- **Met de QR-code**: scan de QR-code op het scherm van de leider met je **camera-app**. Zangbundel opent en zoekt het kampvuur (*Kampvuur zoeken…*, *Gevonden, controleren…*). Heb je de app nog niet, dan opent een webpagina die naar Google Play leidt.

Zie je twee kampvuren met bijna dezelfde naam? Vraag de leider naar **de twee woorden**.

### Het scherm van de zanger

De smalle balk bovenaan toont de naam van het kampvuur, de twee woorden en of je **verbonden** bent.

- **Tik op de twee woorden** om te lezen wat ze betekenen.
- **Tik op de balk** voor de werkbalk: **Dag**/**Nacht**, **Kleiner**/**Groter**, **Passend** (de tekst past in de breedte), **Liedinfo**, de helderheid, **Scherm blijft aan** en **Leider volgen**.
- **Scrollen**: sleep, of tik in de onderste helft (een regel verder) of de bovenste helft (een regel terug).
- **Leider volgen** staat aan als je meedoet: bij elk nieuw lied springt je scherm naar het begin. Scroll je zelf, dan pauzeert het; het volgende lied van de leider zet het weer aan.
- De ronde knoppen: **▲** vorig lied, **≡** de liedjeslijst, **▼** volgend lied. In de lijst kun je ook in je eigen **Zangbundel** bladeren; **Terug naar het huidige lied** brengt je terug.

### Verlaten

Druk op terug (of kies **Kampvuur verlaten** onderaan de lijst) en bevestig met **Verlaten**. Als de leider het kampvuur beëindigt, wordt de balk oranje: tik erop om te verlaten. De liedteksten van de leider worden daarna van je telefoon gewist; de lijst met gezongen liederen blijft als herinnering.

## 8. Herinneringen

**Herinneringen** op het startscherm toont na elk kampvuur de lijst met gezongen liederen, als leider en als zanger. Met **Deel de lijst** deel je hem; **Verwijderen** en **Alles verwijderen** wissen ze. Je bibliotheken blijven.

## 9. Instellingen

- **Naam voor de leider**: niet verplicht; alleen je voornaam of bijnaam. Alleen de leider kan hem lezen; zonder naam ben je een zanger met een nummer.
- **Statistieken van kampvuren** (voor leiders, standaard uit): de telefoon bewaart per kampvuur zonder namen welke liederen er gestuurd werden, hoeveel zangers er waren en hoe goed Bluetooth hen bereikte. Het scherm zegt precies wat wel en niet. Er wordt niets verstuurd; het gaat alleen van je telefoon af als je zelf op **Delen** tikt.
- **Alle gegevens verwijderen**: bibliotheken, setlists, herinneringen en instellingen gaan van de telefoon; de app begint opnieuw zoals op de eerste dag.

## 10. Over en privacy

**Over** toont de versie van de app, de geïnstalleerde bibliotheken, de **Ondertekening** (de vingerafdruk van de echte app), een link naar de broncode en deze handleiding.

In het kort over je gegevens: de app stuurt alleen tijdens een kampvuur iets, via Bluetooth naar telefoons in de buurt: het kampvuur en de liedteksten. Een naam kan alleen de leider lezen. Er gaat niets naar de makers van de app: geen account, geen reclame, geen volgen. Internet gebruikt de app alleen om een bibliotheek of setlist te downloaden die jij kiest. De volledige uitleg staat in de app onder **Wat gebeurt er met mijn gegevens?** en in de [privacyverklaring](https://pe1mew.github.io/Zangbundel/privacy/).

## 11. Als het niet lukt

| Wat je ziet | Wat je kunt doen |
|---|---|
| Geen kampvuren in de lijst | Staat Bluetooth aan en is het toegestaan? Ga dichter bij de leider staan. Of scan de QR-code van de leider. |
| *Ga dichter bij de kampvuurleider staan* | De QR-code is goed, maar het kampvuur is nog niet gehoord. Loop naar de leider en tik op **Opnieuw zoeken**. |
| *Dit is niet het kampvuur van de QR-code* | Een ander kampvuur doet zich voor als dat van de QR-code. De app gaat er niet in. Ga naar start en vertel het de leider. |
| *De leider heeft de code niet aangenomen* | Vraag de deelnamecode nog eens, of scan de QR-code. |
| *Er brandt al een kampvuur* | Je kunt niet starten naast een ander kampvuur: zing daar mee, of ga buiten bereik. |
| *Laad eerst liederen* | Een leider heeft een bibliotheek nodig; zie [Liederen toevoegen](#3-liederen-toevoegen). |
| Zangers raken de verbinding kwijt | Zet bij de leider de batterijbespaarstand uit en houd de telefoon in het midden van de groep. |
| *Deze bibliotheek vraagt een nieuwere versie van de app* | Werk de app bij via Google Play. |

Vragen of fouten? Meld ze op [GitHub](https://github.com/pe1mew/Zangbundel/issues).
