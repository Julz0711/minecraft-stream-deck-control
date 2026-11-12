# Minecraft Stream Deck Control

Control Minecraft from an Elgato Stream Deck, with keys that show live game state.

Toggle the HUD, change perspective, capture a screenshot, adjust settings, or start a cinematic view without hunting through menus.

**Status:** `main` contains this overview. The Fabric mod, Stream Deck plugin, tests, and protocol documentation are on [`claude/project-thread-bs6tar`](https://github.com/Julz0711/minecraft-stream-deck-control/tree/claude/project-thread-bs6tar).

## What the implementation supports

| Area | Controls and feedback |
| --- | --- |
| View | Perspective, HUD, fullscreen, FOV, debug overlay, and cinematic letterboxing. |
| Performance | Render distance, simulation distance, and live FPS. |
| Audio | Master/category volume and mute. |
| Capture | Screenshots and cinematic shots. |
| Communication | Saved chat messages and optionally enabled chat commands. |
| World | Time, weather, daylight cycle, and game mode where permissions allow. |
| Status | Coordinates, clock, compass, biome, server name, ping, and player count. |
| Debug shortcuts | Configurable F3 actions such as hitboxes and chunk borders. |

Administrative controls require cheats in singleplayer or operator rights on a server. Custom chat commands are disabled by default.

## How the parts connect

- **Fabric mod (`mod/`):** runs a paired WebSocket API bound to localhost.
- **Stream Deck plugin (`plugin/`):** discovers the game, displays state, and sends actions.
- **Pairing:** a code shown in Minecraft authorizes the plugin. The plugin searches ports **25570–25579**; a fixed port can select a particular instance.

## Requirements

| Component | Requirement |
| --- | --- |
| Minecraft | 26.3, Java 25, Fabric Loader 0.19.5+, and matching Fabric API |
| Stream Deck | App 7.1+ on Windows 10+ or macOS 12+ |
| Mod Menu | Optional, for mod settings |

## Build the implementation

```sh
git clone --branch claude/project-thread-bs6tar https://github.com/Julz0711/minecraft-stream-deck-control.git
cd minecraft-stream-deck-control
```

Build the mod from `mod/`:

```sh
cd mod
./gradlew build
```

In a separate terminal, from the repository root, build the plugin:

```sh
cd plugin
npm ci
npm test
npm run pack
```

The mod JAR is in `mod/build/libs/`; the packed plugin is in `plugin/dist/`. On Windows, use `gradlew.bat`.

## Pair and use

1. Put the mod JAR in Minecraft's `mods/` folder and install the packed `.streamDeckPlugin` file.
2. Start Minecraft and add a **Minecraft Control** key in the Stream Deck app.
3. Copy the pairing code from Minecraft's toast, chat, or mod settings.
4. Enter it under **Connection** in the key's settings.

Mod configuration lives in `config/stream-deck-control.json`. It controls pairing, the port, command access, pairing toasts, and cinematic settings.

See the [implementation README](https://github.com/Julz0711/minecraft-stream-deck-control/blob/claude/project-thread-bs6tar/README.md) for the full action list and [protocol documentation](https://github.com/Julz0711/minecraft-stream-deck-control/blob/claude/project-thread-bs6tar/docs/protocol.md) for integrating other local tools.

## Development

The mod includes unit tests and `./gradlew runClientGameTest` for in-game API testing. The plugin includes `npm test`, `npm run watch`, and original icon sources in `plugin/src/pixels.ts` and `plugin/src/glyphs.ts`.
