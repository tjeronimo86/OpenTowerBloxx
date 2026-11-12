# OpenTowerBloxx

[Deutsch](README.md) · **English**

OpenTowerBloxx is a tower building game for Android in the style of the classic Tower Bloxx Deluxe 3D. Private hobby project, not commercial.
Kotlin, Jetpack Compose and OpenGL ES 3, Android 8 and later.

[Changelog](changelog/CHANGELOG.en.md) · [Downloads](../../releases/latest)

## How to play

A crane swings the next block above the tower. Tap the screen to drop it.
A perfect drop earns a bonus, fills the combo bar and keeps the tower steady.
Crooked blocks make the tower sway, missed blocks cost a life.

## Modes

- **Quick Game:** as high as you can, three misses
- **Time Attack:** two minutes, as many floors as possible
- **Challenge:** 15 levels with goals, more wind and a faster crane, up to three stars
- **Build City:** 25 plots; townhouse, office and luxury tower need the right neighbours
- **Party:** two players at once on a split screen

## Technology

- 3D graphics with a custom OpenGL renderer: blocks with cornices, skyline, clouds, parachuters, particles
- All textures are drawn at runtime, all sounds and the music are synthesized; the app contains no third-party graphics or recordings
- Day and night look (system, day, night)
- "What's New" and update check via GitHub releases
