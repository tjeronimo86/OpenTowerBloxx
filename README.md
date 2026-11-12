# OpenTowerBloxx

**Deutsch** · [English](README.en.md)

OpenTowerBloxx ist ein Turmbau-Spiel für Android im Stil des Klassikers Tower Bloxx Deluxe 3D. Privates Hobbyprojekt, nicht kommerziell.
Kotlin, Jetpack Compose und OpenGL ES 3, ab Android 8.

[Update-Verlauf](changelog/CHANGELOG.md) · [Downloads](../../releases/latest)

## So wird gespielt

Ein Kran schwingt den nächsten Block über den Turm. Ein Tipp auf den Bildschirm lässt ihn fallen.
Trifft der Block genau, gibt es einen Bonus, die Kombo-Leiste füllt sich und der Turm bleibt ruhig.
Schiefe Blöcke bringen den Turm ins Schwanken, daneben gefallene kosten ein Leben.

## Modi

- **Schnelles Spiel:** so hoch wie möglich, drei Fehlversuche
- **Zeitrennen:** zwei Minuten, so viele Stockwerke wie möglich
- **Herausforderung:** 15 Level mit Ziel, mehr Wind und schnellerem Kran, bis zu drei Sterne
- **Stadt bauen:** 25 Grundstücke; Stadthaus, Bürohaus und Luxusturm brauchen die richtigen Nachbarn
- **Party:** zwei Spieler gleichzeitig mit geteiltem Bildschirm

## Technik

- 3D-Grafik mit eigenem OpenGL-Renderer: Blöcke mit Gesims, Skyline, Wolken, Fallschirmspringer, Partikel
- Alle Texturen werden zur Laufzeit gezeichnet, alle Klänge und die Musik per Synthese erzeugt; die App enthält keine fremden Grafiken oder Aufnahmen
- Tag- und Nachtdarstellung (System, Tag, Nacht)
- „Was ist neu“ und Update-Suche über GitHub-Releases

## Bauen

Jeder Push auf `main` baut per GitHub Actions eine signierte APK und veröffentlicht sie als Release.
Die Emulator-Prüfung (`Actions → Emulator-Pruefung`) spielt alle Modi automatisch und legt Screenshots im Release `verify` ab.
