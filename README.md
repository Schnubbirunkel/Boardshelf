# Boardshelf

**Deine Brettspielsammlung als Regal – auf dem Android-Handy.**
*Your board game collection as a shelf – on your Android phone.* ([English below](#english))

<p align="center">
  <img src="docs/screenshots/de_1_shelf.png" width="200" alt="Regal">
  <img src="docs/take-out.gif" width="200" alt="Schachtel aus dem Regal ziehen">
  <img src="docs/screenshots/de_3_box3d.png" width="200" alt="3D-Ansicht">
</p>
<p align="center">
  <img src="docs/screenshots/de_4_rating.png" width="200" alt="Bewertung">
  <img src="docs/screenshots/de_5_savestates.png" width="200" alt="Spielstände">
  <img src="docs/screenshots/de_6_manuals.png" width="200" alt="Anleitungen">
</p>

## Funktionen

- **Regalansicht** – die Schachteln stehen mit dem Rücken nach vorn auf Regalböden, in echter Größe zueinander (Maße von BoardGameGeek, anpassbar).
- **Umsortieren** – Schachtel lange gedrückt halten und auf einen anderen Platz oder Regalboden ziehen. Regalböden lassen sich anlegen, umbenennen, verschieben und löschen.
- **Herausziehen in 3D** – Antippen zieht die Schachtel aus dem Regal, dreht sie und öffnet die Detailseite. Dort lässt sie sich mit dem Finger frei drehen (Doppeltippen setzt zurück).
- **Spieldaten** von **BoardGameGeek** (Spieler, Dauer, Alter, Autor:innen, Verlage, Mechaniken, BGG-Wertung, Rang, Schachtelgröße) sowie **Wikidata/Wikipedia** (funktioniert auch ohne BGG-Token, Zusammenfassung in deiner Sprache).
- **BGG-Sammlung importieren** – Benutzername eintragen, alle eigenen Spiele inkl. deiner BGG-Wertung landen im Regal.
- **Bewerten** – Gesamtnote (0,5er-Schritte bis 10), Spielspaß, Wiederspielwert, Material & Optik, Regelverständlichkeit und ein Fazit.
- **Spielstände** – Spieltisch fotografieren (oder Foto aus der Galerie), Titel und Notiz dazu („Blau ist dran, 37 Punkte …“). Der neueste Stand ist markiert, Fotos lassen sich zoomen und teilen.
- **Anleitungen** – PDF importieren, per Link laden, im eingebauten Browser suchen (BGG-Dateibereich, DuckDuckGo, Google, 1j1ju – jedes geöffnete PDF kann gespeichert werden) oder die gedruckte Anleitung **mit der Kamera scannen**. Alles offline lesbar, mit Zoom; Scans lassen sich als PDF teilen.
- **Cover-Suche** – auch ohne BGG-Token: in Bildersuche, Amazon oder auf der BGG-Website lange auf das richtige Schachtelbild drücken und übernehmen. Danach lässt sich das Bild **zuschneiden** (Schachtelformat, quadratisch oder frei, drehbar), das geht auch bei Kamerafotos und vorhandenen Covern. Automatisch wird nichts geraten; Wiki-Bilder werden nur genutzt, wenn sie eindeutig die Schachtel zeigen.
- **Eigenes Cover** fotografieren, Daten von Hand bearbeiten, Spiele ohne Online-Eintrag manuell anlegen.
- **Datensicherung** als ZIP (Datenbank, Cover, Fotos, Anleitungen) und Wiederherstellung.
- **Deutsch und Englisch**, umschaltbar in den Einstellungen (oder Systemsprache).
- **Demo-Sammlung** mit ausgedachten Spielen zum Ausprobieren.

## Installation

APK aus dem [neuesten Release](https://github.com/Schnubbirunkel/Boardshelf/releases/latest) herunterladen und installieren (Android 8.0 oder neuer, „Installation aus unbekannten Quellen“ erlauben).

## BoardGameGeek-Token

Seit 2025 verlangt BoardGameGeek für seine XML-API einen kostenlosen Anwendungs-Token.
1. Auf <https://boardgamegeek.com/applications> eine (nicht-kommerzielle) Anwendung registrieren.
2. Den Token in Boardshelf unter **Einstellungen → BoardGameGeek** einfügen.

Ohne Token sucht die App in Wikidata und Wikipedia.

## Bauen

```bash
./gradlew assembleDebug        # app/build/outputs/apk/debug/
./gradlew assembleRelease      # signiert mit dem Schlüssel aus den Umgebungsvariablen (siehe unten)
```

GitHub Actions baut bei jedem Push die APKs; ein Tag `v*` erzeugt ein Release.
Die APKs werden mit dem Projektschlüssel aus `keystore/` signiert, damit gehen Updates direkt über die installierte Version. Optional lässt sich ein eigener geheimer Schlüssel per Secrets hinterlegen: [docs/SIGNING.md](docs/SIGNING.md).

Technik: Kotlin, Jetpack Compose, Room, OkHttp, Coil. Die 3D-Schachtel ist ein eigener kleiner Software-Renderer (perspektivische Texturabbildung per `Matrix.setPolyToPoly`), ganz ohne OpenGL.

---

## English

Boardshelf shows your board game collection as a shelf. Boxes stand with their spines facing out, true to scale. Long-press and drag to rearrange them across shelf boards. Tap a box to watch it get pulled out of the shelf in 3D, turn around and become the detail page, where you can spin it freely.

- Game data from **BoardGameGeek** (needs a free API token from <https://boardgamegeek.com/applications>) and **Wikidata/Wikipedia** (works without a token).
- **Import your BGG collection** including your own ratings.
- **Ratings**: overall score plus fun, replayability, components and rules clarity, and a written review.
- **Game states**: photograph the table to save a running game, with title and note.
- **Rulebooks**: import PDFs, download by link, find them with the built-in browser (BGG files, web search) or **scan the printed rulebook** with the camera. Everything works offline.
- **Cover search** without a BGG token: long-press the right box picture in image search, Amazon or on the BGG website, then **crop** it (box format, square or free).
- Own cover photos, manual editing, ZIP backup and restore, demo collection.
- UI in **English and German**, switchable in the settings.

Download the APK from the [latest release](https://github.com/Schnubbirunkel/Boardshelf/releases/latest).

<sub>Powered by BoardGameGeek. Game data © BoardGameGeek and Wikidata/Wikipedia contributors. The demo games are fictional.</sub>
