# Footy – Schrittzähler ohne Werbung

Schlanke Android-App (~60 KB), keine Werbung, keine Internet-Berechtigung, keine Fremdbibliotheken.

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

## Installation
1. APK aus GitHub → *Actions* → letzter Lauf → Artefakt **Footy-apk** laden (oder selbst bauen: `./gradlew assembleRelease`).
2. Auf dem Handy öffnen, „Unbekannte Apps installieren“ erlauben.
3. Berechtigung **Körperliche Aktivität** erlauben.
4. Empfohlen (Samsung): Menü ⋮ → *Akku-Optimierung ausschalten*, damit Android die Zählung nicht beendet.

Hinweis: Die App ist mit dem im Repo liegenden Schlüssel `app/footy.keystore` signiert, damit Updates
ohne Datenverlust installiert werden können. Nur für private Nutzung gedacht.

## Berechnung
- Strecke = Schritte × Schrittlänge
- Gehzeit = Zeit zwischen Schritt-Ereignissen (max. 0,75 s pro Schritt)
- Kalorien = MET(Geschwindigkeit) × Gewicht × Gehzeit
