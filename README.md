# Ashen Prototype – Android

Android port of the pure-Java 2D souls-like prototype. Content is data-driven:

| What | Where | Edit with |
|---|---|---|
| Level layout (platforms, bonfires, enemy spawns, boss gate, scenery, backgrounds, ground look) | `app/src/main/assets/maps/level1.tmx` | **Tiled** |
| Art used by the map | `maps/art.tsx` (+ images in `assets/textures/`) | Tiled (tileset) |
| HUD, bonfire menu, touch button layout and labels | `app/src/main/assets/ui/ui.xml` | any text editor |
| Player, weapons, enemies, physics, combat tuning | `app/src/main/assets/data/*.json` | any text editor |
| Sprite animation timing and frame lists | `data/player.json`, `data/enemies.json` | any text editor |

## Open and run
1. Open this folder in **Android Studio** (Hedgehog or newer; it bundles JDK 17).
2. Let Gradle sync, plug in a phone (or start an emulator), press **Run**.
3. Landscape only. Minimum Android 7.0 (API 24).

Headless tests (no device needed): `./gradlew test`

## Editing the level in Tiled
Open `app/src/main/assets/maps/level1.tmx` in Tiled (1.10 or newer). Everything is a normal Tiled object, and
object positions are in pixels (the grid is 32 px only to help you snap):

- **Map properties** (Map > Map Properties): `levelWidth`, `groundY` (the floor line), `playerStart`,
  `skyColor`, `groundColor`, `platformColor`, `groundTop`, `groundFill`, `groundScale`, `platformTexture`,
  `platformScale`, `bonfireOff`, `bonfireOn`, `bonfireScale`. Textures are paths under `textures/`, for example
  `environment/grass_t.png`.
- **backgrounds** layer: image objects. The `factor` property sets parallax (0 = fixed, larger = moves more).
  Their bottom edge should sit at y = 540 (the screen height).
- **scenery** layer: image objects standing on the floor. Place the bottom edge on `groundY`.
- **platforms** layer: rectangles. The top edge is the walkable surface.
- **bonfires** layer: points. Object name = bonfire id, property `label` = name shown to the player.
- **enemies** layer: points. Property `enemy` = an id from `data/enemies.json` (`hollow`, `brute`, `warden`).
- **gate** layer: one image object. Its horizontal centre is the boss-gate line. Property `boss` = the boss id.

Use the image collection tileset `art.tsx` to pick images for scenery, backgrounds and the gate. To add a new
decoration image, add it to `assets/textures/environment/` and add a tile for it in `art.tsx`.
Other layers in the map are ignored, so you can keep notes or helper layers.

## Editing the UI (`ui/ui.xml`)
Each element has an `id`, and the game looks it up by id. Positions are in the 960x540 logical screen:
`x` / `y` are absolute, `cx` is an offset from the horizontal centre, and `cy` from the vertical centre.
For example `<text id="banner" cy="-40" .../>` draws the banner 40 units above the middle of the screen.
Colours are `#rrggbb`. Labels are `text` attributes.

Touch buttons live in the `<touch>` block. `group="game"` buttons appear during play and `group="menu"` buttons
replace them while the bonfire menu is open. Movement is an analog joystick (the `move` element in ui.xml). Other buttons have an `action`, which is one of the game's input actions:
`jump`, `light`, `heavy`, `block`, `roll`, `flask`, `interact`, `switchWeapon`, `debug`,
`menuUp`, `menuDown`, `back`. Set `x`, `y` and radius `r` to move it.

## Controls (on-screen)
| Button | Action | Desktop key |
|---|---|---|
| stick (bottom-left) | move | A / D |
| JUMP | jump | W |
| LIGHT | light combo | J |
| HEAVY | heavy attack | K |
| BLOCK (hold) | block / parry | L |
| ROLL | i-frame roll | SPACE |
| FLASK | estus flask | R |
| USE | bonfire rest / confirm | E |
| SWAP | switch weapon | Q |
| BOX | toggle hitboxes (debug) | F1 |

Several fingers work at once (move + attack + block).

## Attack and drink animations
Sprite animations are described in `data/player.json` and `data/enemies.json`:

- `frames`: an explicit list of cell indices in the sheet (use this to skip cut-off or empty cells).
- `phases`: frame lists for an attack's `windup`, `active` and `recovery`. The active frame plays exactly during the
  hit window, so the strike lines up with damage. The heavy attack and the greataxe smash use this.
- `feet` / `cx`: pixel anchor inside a frame. It must match where the character's feet sit in the sheet.

## Layout
```
app/src/main/assets/maps/level1.tmx    level layout (Tiled)
app/src/main/assets/maps/art.tsx       image tileset used by the map
app/src/main/assets/ui/ui.xml          HUD, menu, touch layout, version label
gradle.properties                      app version (appVersionName / appVersionCode)
CHANGELOG.md                           release history
app/src/main/assets/data/*.json        player, weapons, enemies, physics, items
app/src/main/java/com/ashen/prototype/
  Json, Data, Box, Input, Actor, Player, Enemy, World     core game (pure Java)
  Xml, Ui, TmxLoader                                      XML loaders (pure Java)
  Assets, Renderer                                        loading + drawing (Android)
  TouchControls, GameView, MainActivity                   input, loop, activity (Android)
app/src/test/java/com/ashen/prototype/CoreSelfTest.java  headless tests
```

## Versions
The version lives in `gradle.properties` (`appVersionName`, `appVersionCode`) and is shown in-game from
`ui/ui.xml` (element `version`). When you release, change both, add a section to `CHANGELOG.md`, and
bump `appVersionCode` by one. Zip releases are named `AshenPrototype-Android-v<version>.zip`.

## Known limits
- The core logic, map loading and UI loading are tested headlessly. The Android layer was type-checked against
  Android API stubs but has not yet been run on a device, so check the layout on your phone.
- Animations were corrected from the sheet contents (frame positions and timing measured from the PNGs). Confirm the
  heavy strike and the drink look right on the device, and adjust `phases` / `frames` in `player.json` if needed.
- `weapons/longsword.png` and `weapons/greataxe.png` are referenced by `weapons.json` but were not in the
  source project, so weapons use their rectangle fallback until art is added.
- Art licences: see the packs listed in the original README (Samurai bundle, Phewcumber skeletons, Pixel
  Platformer tilesets).
