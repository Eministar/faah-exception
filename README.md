# FAH Failure Sound - JetBrains Plugin

<p align="center">
  <img src="src/main/resources/icon.png" alt="FAH Failure Sound Icon" width="140" />
</p>

<p align="center">
  <b>Mach Build-/Run-Ergebnisse hörbar</b> 🔊<br/>
  Meme-Sounds für Build, Run, Gradle und Maven - inklusive Fehler, Erfolg und Warning.
</p>

<p align="center">
  <img alt="JetBrains" src="https://img.shields.io/badge/JetBrains%20IDEs-2024.3.5-blue?logo=intellijidea" />
  <img alt="Java" src="https://img.shields.io/badge/Java-17-orange?logo=openjdk" />
  <img alt="Gradle" src="https://img.shields.io/badge/Gradle-Plugin%20Project-02303A?logo=gradle" />
  <img alt="Status" src="https://img.shields.io/badge/Status-Active-success" />
</p>

---

## ✨ Warum dieses Plugin?

Wenn du beim Entwickeln nicht ständig auf Build-/Run-Ausgaben schauen willst, spielt dieses Plugin passende Sounds bei JetBrains-IDE-Events ab. So merkst du sofort, ob etwas schiefgelaufen ist - oder ob du feiern kannst. 🎉

## 🖼️ Preview

| Ansicht | Preview |
|---|---|
| Plugin Icon | <img src="src/main/resources/icon.png" alt="Plugin Icon" width="72" /> |
| Settings-Bereich | <img src="src/main/resources/icon.png" alt="Settings Placeholder" width="72" /><br/>`Settings > Tools > FAH Failure Sound` |

## 🚀 Features auf einen Blick

- ✅ Sounds für Build, Run/Debug, Gradle und Maven-Events
- ⚙️ Pro Event eigener Sound, eigenes Bild/GIF und eigene maximale Abspielzeit (`Max ms`)
- 🎚️ Lautstärke (`0-100`) und Debounce (`ms`) konfigurierbar
- 🖼️ Bilder und GIFs pro Event direkt in der IDE einblendbar
- 📦 Gebündelte Defaults: `error.mp3`, `succeed.mp3`, `cooked.jpg`, `happy.gif`
- 🧩 Eigene Audiodateien (`.mp3`, `.wav`) aus Custom-Ordner nutzbar
- 🎞️ Eigene Media-Dateien (`.png`, `.jpg`, `.jpeg`, `.gif`) aus Custom-Ordner nutzbar
- 🔕 `No sound` pro Event möglich
- 🧪 Pro Event direkt testbar per `Test`-Button in den Settings, inkl. Sound + Bild/GIF

## 🎯 Trigger-Matrix

| Event | Wann wird getriggert? | Sound Default | Bild/GIF Default |
|---|---|---|
| `Build failed` | JetBrains IDE Build mit Fehler | `Bundled / error.mp3` | `Bundled / cooked.jpg` |
| `Build warning` | Build mit Warnings | `Bundled / error.mp3` | `No image / GIF` |
| `Build succeeded` | Erfolgreicher Build | `Bundled / succeed.mp3` | `No image / GIF` |
| `Run failed` | Run/Debug mit `exitCode != 0` | `Bundled / error.mp3` | `Bundled / cooked.jpg` |
| `Run succeeded` | Run/Debug mit `exitCode == 0` | `No sound` | `No image / GIF` |
| `Gradle failed` | JetBrains IDE Gradle Runner meldet Fehler | `Bundled / error.mp3` | `Bundled / cooked.jpg` |
| `Maven failed` | JetBrains IDE Maven Runner meldet Fehler | `Bundled / error.mp3` | `Bundled / cooked.jpg` |

## ⚙️ Konfiguration

| Option | Beschreibung | Werte |
|---|---|---|
| `Enable sounds` | Plugin global ein-/ausschalten | `true/false` |
| `Volume` | Gesamtlautstärke | `0-100` |
| `Debounce (ms)` | Verhindert zu häufiges Triggern in kurzer Zeit | `0-60000` |
| `Show notification` | Zus. Balloon-Notification anzeigen | `true/false` |
| `Show image / GIF overlays` | Aktiviert IDE-Overlays für die pro Event gewählten Media-Dateien | `true/false` |
| `Sound pro Event` | Quelle je Event wählen | `Bundled`, `Custom`, `No sound` |
| `Bild/GIF pro Event` | Quelle je Event wählen | `Bundled`, `Custom`, `No image / GIF` |
| `Max ms (0=full)` | Max. Abspielzeit pro Event | `0-300000` |

### 📁 Custom Sounds

- Ordner: `<IDE config>/faah-sounds`
- Unterstützt: `.mp3`, `.wav`
- In Settings:
  1. `Open sounds`
  2. Dateien reinlegen
  3. `Refresh sounds`
  4. Sound pro Event auswählen

### 🖼️ Custom Media

- Ordner: `<IDE config>/faah-media`
- Unterstützt: `.png`, `.jpg`, `.jpeg`, `.gif`
- In Settings:
  1. `Open media`
  2. Dateien reinlegen
  3. `Refresh media`
  4. Bild/GIF pro Event auswählen

## 🧱 Projektstruktur

```text
src/main/resources/META-INF/plugin.xml
src/main/resources/META-INF/pluginIcon.svg
src/main/resources/META-INF/pluginIcon_dark.svg
src/main/resources/icon.png
src/main/resources/error.mp3
src/main/resources/happy.gif
src/main/resources/succeed.mp3
src/main/java/dev/eministar/fahsound
```

## 🛠️ Entwicklung & Build

### Lokale Sandbox starten (Windows PowerShell)

```powershell
.\gradlew.bat runIde
```

### Plugin ZIP bauen

```powershell
.\gradlew.bat buildPlugin
```

### Plugin in einer JetBrains IDE installieren

1. Gewünschte JetBrains IDE öffnen
2. `Settings > Plugins`
3. Zahnrad > `Install Plugin from Disk...`
4. ZIP aus `build/distributions/` wählen

## 🏪 Marketplace-Readiness

### Benötigte Umgebungsvariablen

| Variable | Zweck |
|---|---|
| `JB_CERTIFICATE_CHAIN` | Zertifikatskette für Signierung |
| `JB_PRIVATE_KEY` | Private Key |
| `JB_PRIVATE_KEY_PASSWORD` | Passwort zum Private Key |
| `JB_PUBLISH_TOKEN` | Token für Marketplace Upload |

### Tasks

```powershell
.\gradlew.bat signPlugin
.\gradlew.bat publishPlugin
```

## ⚠️ Grenzen / Hinweise

- Trigger beziehen sich auf JetBrains-IDE-Events (nicht auf beliebige Prozesse außerhalb der IDE).

## 📄 Lizenz

Dieses Projekt steht unter der MIT-Lizenz. Siehe `LICENSE`.

---

<p align="center">
  Mit ❤️ gebaut von <b>Eministar</b> für mehr Feedback beim Coden.
</p>
