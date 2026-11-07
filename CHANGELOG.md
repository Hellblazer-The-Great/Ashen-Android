# Changelog

All notable changes to Ashen Prototype (Android). Versions follow `MAJOR.MINOR.PATCH`; `versionCode` in
`gradle.properties` increases with every release.

## 0.2.0 (versionCode 2)
- Level is a Tiled map (`maps/level1.tmx`), editable in Tiled.
- HUD, bonfire menu and touch button layout moved to `ui/ui.xml`.
- Heavy attack: feet anchor corrected (figure no longer floats), strike frame now lands in the hit window
  using per-phase frames; greataxe smash uses the same fix.
- Heal (flask) animation uses the full-body frames instead of cut-off fragments.
- Version number shown in the corner of the game screen.

## 0.1.0 (versionCode 1)
- First Android port of the desktop prototype: on-screen touch controls, JSON-driven content,
  headless core tests.
