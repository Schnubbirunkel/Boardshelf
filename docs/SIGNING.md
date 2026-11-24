# Signaturschlüssel für Boardshelf einrichten

> **Standard (nichts zu tun):** Alle Builds werden mit dem öffentlichen Projektschlüssel `keystore/boardshelf-public.jks` signiert (wie bei Barkeep). Damit gehen Updates direkt über die installierte Version. Nachteil: Der Schlüssel ist öffentlich.
>
> **Optional, sicherer:** einen eigenen, geheimen Schlüssel als GitHub-Secret hinterlegen (Anleitung unten). Ist er gesetzt, hat er Vorrang. Beim Wechsel muss die App einmal neu installiert werden.

Android installiert ein Update nur, wenn die neue APK mit **demselben Schlüssel** signiert ist wie die installierte.
Ohne eigenen Schlüssel signiert GitHub jede APK mit einem neuen Zufallsschlüssel, dann geht ein Update nur über Deinstallieren (und dabei sind die Daten weg).

Du brauchst **einmalig** einen Schlüssel und **vier Secrets** im Repo.

## 1. Schlüssel (Keystore) erzeugen

Du brauchst `keytool`. Es gehört zu jedem Java-JDK. Wenn Android Studio installiert ist, liegt es hier:
- Windows: `C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe`
- macOS: `/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/keytool`
- Linux: `<Android-Studio-Ordner>/jbr/bin/keytool`, oder `sudo apt install openjdk-17-jdk-headless`

Im Terminal (Windows: PowerShell) in einem Ordner deiner Wahl:

```
keytool -genkeypair -v -keystore boardshelf-release.jks -alias boardshelf -keyalg RSA -keysize 4096 -validity 36500
```

- Es wird zweimal nach einem **Passwort** gefragt. Nimm ein langes und merk es dir.
- Danach kommen Fragen nach Name, Organisation usw. Die Antworten sind egal, z. B. nur beim Namen etwas eintragen und den Rest mit Enter überspringen. Am Ende mit `ja`/`yes` bestätigen.
- Ergebnis: die Datei `boardshelf-release.jks`.

## 2. Keystore in Text (Base64) umwandeln

GitHub-Secrets können nur Text speichern, deshalb wird die Datei in Base64 umgewandelt:

| System | Befehl |
|---|---|
| Windows (PowerShell) | `[Convert]::ToBase64String([IO.File]::ReadAllBytes("$PWD\boardshelf-release.jks")) \| Set-Content keystore.txt` |
| macOS | `base64 -i boardshelf-release.jks -o keystore.txt` |
| Linux | `base64 -w0 boardshelf-release.jks > keystore.txt` |

`keystore.txt` enthält jetzt eine sehr lange Zeile Text.

## 3. Secrets bei GitHub anlegen

Im Repo **Boardshelf** → **Settings** → links **Secrets and variables** → **Actions** → Button **New repository secret**.
Lege diese vier Secrets an (Name exakt so, Großbuchstaben):

| Name | Inhalt |
|---|---|
| `KEYSTORE_B64` | der komplette Inhalt von `keystore.txt` (alles markieren, kopieren, einfügen) |
| `KEYSTORE_PASSWORD` | das Passwort aus Schritt 1 |
| `KEY_ALIAS` | `boardshelf` |
| `KEY_PASSWORD` | dasselbe Passwort wie bei `KEYSTORE_PASSWORD` |

Ab dem nächsten Release-Lauf werden alle APKs mit deinem Schlüssel signiert.

## 4. Gut aufbewahren

- `boardshelf-release.jks` und das Passwort an einem sicheren Ort speichern, z. B. im Passwortmanager. **Geht der Schlüssel verloren, gibt es keine Updates mehr**, nur noch Neuinstallation.
- Die Datei **nicht** ins Repo hochladen. `keystore.txt` kannst du danach löschen.

## 5. Einmaliger Wechsel auf dem Handy

Deine jetzt installierte Version ist noch mit einem Zufallsschlüssel signiert. Für den Wechsel deshalb einmalig:
1. In Boardshelf **Einstellungen → Datensicherung → Exportieren** und die ZIP-Datei speichern.
2. Boardshelf deinstallieren und die neu signierte APK installieren.
3. **Einstellungen → Datensicherung → Wiederherstellen** und die ZIP-Datei auswählen.

Danach gehen alle weiteren Updates direkt drüber.
