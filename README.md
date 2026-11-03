# Footy – Schrittzähler ohne Werbung

Schlanke Android-App für Handy **und Wear-OS-Uhr** (z. B. OnePlus Watch 2), keine Werbung,
keine Internet-Berechtigung. Einzige Fremdbibliothek: Googles Wear-Datenschicht für den Abgleich Uhr → Handy.

## Funktionen
- Tabs **Tag / Woche / Monat**, mit Pfeilen durch die Zeiträume blättern
- Schritte, Kalorien, Strecke (m), Gehzeit, Ø-Geschwindigkeit
- **Kachel antippen → Diagramm wechselt** auf Kalorien, Strecke, Zeit oder Geschwindigkeit
  (erneut antippen oder Schrittzahl antippen → zurück zu Schritten)
- Tag: Linien­diagramm (kumuliert pro Stunde), Woche/Monat: Balken pro Tag
- **START/STOP** der Zählung
- Menü (⋮): Schrittlänge, Gewicht, Tagesziel · Akku-Optimierung ausschalten · Daten löschen

## Stromsparen
- Nutzt den **Hardware-Schrittzähler** (`TYPE_STEP_COUNTER`): der Sensor-Chip zählt selbst,
  die CPU wird dafür nicht geweckt.
- Im Hintergrund werden Sensor-Ereignisse bis zu 10 Min. gebündelt; live nur bei geöffneter App.
- Kein GPS, kein Wakelock, kein Netzwerk. Benachrichtigung höchstens 1×/Minute aktualisiert.

## Uhr (Wear OS)
- Die Uhr zählt selbst (gleiche stromsparende Technik, schwarzer Hintergrund für AMOLED).
- Gleiche Ansicht: Tag/Woche/Monat, Kacheln antippen wechselt das Diagramm, START/STOP.
- **Abgleich:** Die Uhr schickt ihre Stundenwerte ans Handy (höchstens alle 10 Min., gebündelt).
  Das Handy zeigt alles zusammen: pro Stunde zählt das Gerät mit **mehr** Schritten –
  so wird beim gleichzeitigen Tragen nichts doppelt gezählt.
- Schrittlänge, Gewicht und Tagesziel werden am Handy eingestellt und an die Uhr übertragen.
- Handy und Uhr müssen über die Wear-OS-Begleit-App (bei OnePlus: „OHealth“) gekoppelt sein.

### Uhr-App installieren (OnePlus Watch 2)
Es gibt keinen Play Store für selbst gebaute Apps, daher per ADB:
1. Uhr: *Einstellungen → System → Info* → 7× auf *Build-Nummer* tippen (Entwickleroptionen).
2. *Entwickleroptionen → ADB-Debugging* und *Debugging über WLAN* einschalten; IP:Port notieren.
3. Am PC: `adb pair IP:PAIRPORT` (Code von der Uhr), dann `adb connect IP:PORT`
   und `adb install Footy-Uhr.apk`.
   Ohne PC geht es mit Handy-Apps wie „Bugjaeger“ oder „Wear Installer 2“.
4. Auf der Uhr Footy öffnen und *Körperliche Aktivität* erlauben.

## Installation (Handy)
1. APK aus GitHub → *Actions* → letzter Lauf → Artefakt **Footy-apk** laden (enthält `Footy-Handy.apk` und `Footy-Uhr.apk`) (oder selbst bauen: `./gradlew assembleRelease`).
2. Auf dem Handy öffnen, „Unbekannte Apps installieren“ erlauben.
3. Berechtigung **Körperliche Aktivität** erlauben.
4. Empfohlen (Samsung): Menü ⋮ → *Akku-Optimierung ausschalten*, damit Android die Zählung nicht beendet.

Hinweis: Die App ist mit dem im Repo liegenden Schlüssel `app/footy.keystore` signiert, damit Updates
ohne Datenverlust installiert werden können. Nur für private Nutzung gedacht.

## Berechnung
- Strecke = Schritte × Schrittlänge
- Gehzeit = Zeit zwischen Schritt-Ereignissen (max. 0,75 s pro Schritt)
- Kalorien = MET(Geschwindigkeit) × Gewicht × Gehzeit
