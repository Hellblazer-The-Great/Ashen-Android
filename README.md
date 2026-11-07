# Ashen Prototype – Android

Android port of the pure-Java 2D souls-like prototype. Same data-driven design: all content lives in
`app/src/main/assets/data/*.json`, all art in `app/src/main/assets/textures/`.

## Open and run
1. Open this folder in **Android Studio** (Hedgehog or newer; it bundles JDK 17).
2. Let Gradle sync, plug in a phone (or start an emulator), press **Run**.
3. Landscape only. Minimum Android 7.0 (API 24).

Command line (needs a Gradle install or the wrapper jar):
`gradle wrapper && ./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`

Headless logic test (no device needed): `./gradlew test`

## Controls (on-screen, replaces the keyboard)
| Button | Action | Desktop key |
|---|---|---|
| < / > | move | A / D |
| JUMP | jump | W |
| LIGHT | light combo | J |
| HEAVY | heavy attack | K |
| BLOCK (hold) | block / parry | L |
| ROLL | i-frame roll | SPACE |
| FLASK | estus flask | R |
| USE | bonfire rest / confirm | E |
| SWAP | switch weapon | Q |
| BOX | toggle hitboxes (debug) | F1 |

Bonfire menu: UP / DOWN / OK / BACK replace the buttons while the level-up menu is open.
Several fingers work at once (move + attack + block).

## What changed from the PC version
| Desktop (Swing) | Android |
|---|---|
| `JFrame` + `JPanel` + `javax.swing.Timer` | `Activity` + `View` drawn on `Canvas`, driven by `Choreographer` (vsync) |
| `KeyListener` + `controls.json` | `TouchControls` (multi-touch on-screen buttons); `controls.json` removed |
| `Files.readString` / `ImageIO` / `File` | `AssetManager` (`assets/data`, `assets/textures`), `BitmapFactory` |
| `BufferedImage.getSubimage` | `Bitmap.createBitmap` |
| `java.awt.geom.Rectangle2D` | `Box` (same overlap rule) |
| `java.awt.Color.decode` | `Color.parseColor` |
| Java records, pattern matching, switch arrows | Plain Java 8 classes/syntax (`minSdk 24`) |
| 960x540 window, fixed | 960x540 logical screen, letterboxed to any phone size |
| Keyboard hint line | Removed (buttons are labelled instead) |

The core game code (`Json`, `Data`, `Actor`, `Player`, `Enemy`, `World`, `Input`) contains no Android or
Swing imports, so it can be unit-tested on the JVM (`CoreSelfTest`).

## Layout
```
app/src/main/java/com/ashen/prototype/
  Json, Data, Box, Input, Actor, Player, Enemy, World   core game (pure Java)
  Assets, Renderer                                       loading + drawing (Android)
  TouchControls, GameView, MainActivity                  input, loop, activity (Android)
app/src/test/java/com/ashen/prototype/CoreSelfTest.java  headless simulation
```

## Tuning and content
Everything the PC version exposed still works by editing JSON: add enemies in `enemies.json` and a spawn in
`level.json`, weapons in `weapons.json` (and their id in `player.json`), stats in `player.json`.
Set `"useTextures": false` in `game.json` to draw plain rectangles.

## Known limits
- The core game logic was run headlessly (CoreSelfTest). The Android layer (rendering, touch, activity) was
  type-checked against Android API stubs but has not yet been run on a device, so check the on-screen
  button layout on your phone and adjust the coordinates in TouchControls if needed.
- `weapons/longsword.png` and `weapons/greataxe.png` are referenced by `weapons.json` but were not in the
  source project, so weapons use their rectangle fallback until art is added.
- Art licences: see the packs listed in the original README (Samurai bundle, Phewcumber skeletons, Pixel
  Platformer tilesets).
