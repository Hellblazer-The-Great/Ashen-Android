# Changelog

All notable changes to Ashen Prototype (Android). Versions follow `MAJOR.MINOR.PATCH`; `versionCode` in
`gradle.properties` increases with every release.

## 0.3.0 (versionCode 4)
- Movement is an analog joystick (floating stick in the bottom-left) instead of the < and > buttons.
  Walking eases up to speed and eases to a stop, so motion is smoother.
- Rolling follows the stick direction.

## 0.2.1 (versionCode 3)
- Heavy attack and greataxe smash now use the attack1 sheet, whose frames share one ground line; fixes the
  figure jumping above and below its position during the attack.
- Touch button labels are centred with font metrics.

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
