# Stream Deck Control

Control Minecraft from an Elgato Stream Deck. One press toggles the HUD, switches perspective, starts a cinematic shot
or sets the time, and the keys show what's going on in the game: render distance, FOV, volume, FPS, coordinates and
the in-game clock.

This repository has two parts:

- **[mod/](mod)**: a client-side Fabric mod for Minecraft 26.3. It runs a small WebSocket API on your own PC
  (localhost only), documented in [docs/protocol.md](docs/protocol.md), so other tools can use it too.
- **[plugin/](plugin)**: the Stream Deck plugin ("Minecraft Control") that talks to the mod.

## Keys

| Key | Shows | |
| --- | --- | --- |
| Render distance + / − | Chunks | |
| Simulation distance + / − | Chunks | Singleplayer |
| Perspective | 1st / 3rd / front | F5 |
| Hide HUD | Hidden or shown | F1 |
| Cinematic mode | On or off | Smooth camera, letterbox bars (2.39:1 by default), no HUD |
| Screenshot | Flashes on capture | F2 |
| Debug screen | On or off | F3 |
| Field of view + / − | FOV | |
| Volume + / − / mute | Percent | Master or one sound category (music, jukebox/note blocks, hostile mobs, ...) |
| Fullscreen | On or off | F11 |
| Open menu | | Pause menu, chat or inventory |
| Chat message | Your label | Sends a saved message, e.g. `gg` or `I'm at {coords}` |
| Chat command | Your label | Any command; off until enabled in the mod settings, optional press-twice confirm |
| Set weather | Current weather highlighted | Admin |
| Set time | In-game clock | Admin |
| Daylight cycle | On or off | Admin |
| Game mode | Current mode | Admin |
| Status | FPS, coordinates or in-game time | Read-only |

Admin keys need operator rights on a server, or cheats in singleplayer. Without them they show a lock and do nothing,
instead of sending commands the server would reject.

## Setup

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) 0.19.5+ and [Fabric API](https://modrinth.com/mod/fabric-api)
   for Minecraft 26.3, and put the Stream Deck Control jar in your `mods` folder.
   [Mod Menu](https://modrinth.com/mod/modmenu) is optional and adds the settings screen.
2. Install the plugin: double-click `com.julz.minecraft-control.streamDeckPlugin` (Stream Deck 7.1 or newer,
   Windows 10+ or macOS 12+).
3. Start Minecraft and drag a Minecraft Control key onto your Stream Deck. The key says **PAIR** and Minecraft shows
   a toast with a pairing code such as `K7QM-2XPA`.
4. Click the key in the Stream Deck app and enter the code under **Connection**. Done; the code is remembered.

The plugin finds the game on its own (ports 25570 to 25579). With several Minecraft instances running, it connects to
the first one; set a fixed port in the key settings to pick another.

## Mod settings

Mod Menu → Stream Deck Control, or `config/stream-deck-control.json`:

| Setting | Default | |
| --- | --- | --- |
| `port` | 25570 | First port to try |
| `pairingCode` | random | Click it in the settings screen for a new one; paired decks must pair again |
| `allowCommands` | false | Lets Chat command keys run commands |
| `showPairingToasts` | true | Toast with the code when an unpaired client connects |
| `letterboxRatio` | 2.39 | Picture shape for cinematic mode, 0 for no bars |
| `cinematicHidesHud` | true | Cinematic mode also hides the HUD |
| `allowedOrigins` | [] | Web pages allowed to use the API |

## Building

```sh
cd mod && ./gradlew build              # mod jar in mod/build/libs
cd mod && ./gradlew runClientGameTest  # game test against the real API
cd plugin && npm ci && npm test        # plugin unit tests
cd plugin && npm run pack              # dist/com.julz.minecraft-control.streamDeckPlugin
cd plugin && npm run watch             # rebuilds and restarts the plugin in the Stream Deck app while developing
```

`npm run icons` regenerates the plugin's images from the glyphs in `plugin/src/glyphs.ts`.

GitHub Actions builds both parts on every pull request and uploads the mod jar and the `.streamDeckPlugin` as artifacts.
