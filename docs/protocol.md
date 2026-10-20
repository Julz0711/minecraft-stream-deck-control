# Local API (protocol version 1)

The mod runs a WebSocket server on `127.0.0.1`, port **25570** by default. If the port is taken (for example by a
second Minecraft instance), it takes the next free one, up to 25579. The Stream Deck plugin uses this API, and so can
anything else: Touch Portal, Loupedeck, AutoHotkey, a Python script.

All messages are JSON text frames with a `type` field.

## Security

- The server only listens on the loopback address, so nothing outside your PC can reach it.
- A client must send the **pairing code** in its `hello`. The code is shown in a Minecraft toast when an unpaired
  client connects, and in the mod's settings screen. It is stored in `config/stream-deck-control.json`.
- Connections that send a browser `Origin` header are refused unless the origin is listed in `allowedOrigins` in the
  config, so web pages can't drive the game. Programs that send no `Origin` header (the plugin, scripts) are fine.
- The `command` action is off until **Chat command keys** is turned on in the settings.

## Handshake

Client → mod, within 10 seconds of connecting:

```json
{ "type": "hello", "protocol": 1, "token": "K7QM-2XPA", "client": "My script 1.0" }
```

The code is matched ignoring case, spaces and dashes. On success the mod answers with the actions this version
supports and the full current state:

```json
{
  "type": "welcome",
  "protocol": 1,
  "mod": "1.0.0",
  "minecraft": "26.3",
  "actions": ["cinematic", "command", "daylight_cycle", "..."],
  "state": { "inWorld": true, "renderDistance": 12, "...": "..." }
}
```

Otherwise it sends an `error` and closes the connection:

| `code` | Close code | Meaning |
| --- | --- | --- |
| `pairing_required` | 4001 | Missing or wrong pairing code. Minecraft shows the code in a toast. |
| `unsupported_protocol` | 4002 | The client speaks another protocol version. |
| (none) | 4003 | No `hello` within 10 seconds. |

## Actions

```json
{ "type": "action", "id": "42", "name": "render_distance", "args": { "delta": 2 } }
```

`id` is any string or number; the mod echoes it in the result. `args` is optional.

```json
{ "type": "result", "id": "42", "ok": true }
{ "type": "result", "id": "43", "ok": false, "error": "no_permission", "message": "Needs operator rights or cheats" }
```

| Action | Args | Notes |
| --- | --- | --- |
| `render_distance` | `delta` (default 1) or `value` | Clamped to the game's range. |
| `simulation_distance` | `delta` (default 1) or `value` | Singleplayer only. |
| `fov` | `delta` (default 5) or `value` | 30 to 110. |
| `volume` | `delta` (default 10) or `value`, in percent | Master volume. |
| `mute` | `on` (optional) | Toggles; unmuting restores the previous volume. |
| `fullscreen` | `on` (optional) | Toggles. |
| `perspective` | `to` (optional): `first_person`, `third_person_back`, `third_person_front` | Cycles like F5. |
| `hide_hud` | `on` (optional) | Like F1. |
| `cinematic` | `on` (optional) | Smooth camera, letterbox bars, HUD hidden (configurable). Ends when you leave the world. |
| `debug_overlay` | `on` (optional) | Like F3. |
| `screenshot` | | Like F2. |
| `open_screen` | `screen`: `pause`, `chat`, `inventory` | Chat and inventory fail with `screen_open` while a menu is open. |
| `command` | `command`, with or without `/` | Needs **Chat command keys** turned on. |
| `weather` | `weather`: `clear`, `rain`, `thunder` | Admin. |
| `time` | `time`: `day`, `noon`, `night`, `midnight` | Admin. |
| `daylight_cycle` | `on` (optional) | Admin. Sets the `advance_time` game rule. |
| `game_mode` | `mode` (optional): `survival`, `creative`, `adventure`, `spectator` | Admin. Without `mode`, cycles survival, creative, spectator. |

Toggle actions flip the current value, or set it when `on` is given. Admin actions need operator rights on a server, or
cheats in singleplayer (`canCheat` in the state); without them they fail with `no_permission` and nothing is sent to
the server.

Error codes: `bad_request`, `not_paired`, `unknown_action`, `not_in_world`, `no_permission`, `singleplayer_only`,
`screen_open`, `disabled`, `internal`.

## State

After the welcome, the mod pushes the keys that changed, at most once per game tick (20 per second):

```json
{ "type": "state", "state": { "fps": 143, "position": { "x": 12, "y": 64, "z": -40 } } }
```

A key set to `null` was removed (for example `position` after leaving the world).

| Key | Type | In a world only |
| --- | --- | --- |
| `inWorld` | boolean | |
| `singleplayer` | boolean | |
| `renderDistance`, `simulationDistance`, `fov` | number | |
| `volume` | number, percent | |
| `muted`, `fullscreen`, `hudHidden`, `debugOverlay`, `cinematic` | boolean | |
| `fps` | number | |
| `perspective` | `first_person`, `third_person_back`, `third_person_front` | |
| `canCheat` | boolean | yes |
| `position` | `{ x, y, z }`, block coordinates | yes |
| `time` | `{ day, timeOfDay }`, ticks 0 to 23999 where 0 is 06:00 | yes |
| `weather` | `clear`, `rain`, `thunder` | yes |
| `gameMode` | `survival`, `creative`, `adventure`, `spectator` | yes |
| `daylightCycle` | boolean, known about a second after joining | yes |

## Example

```python
import json, websocket  # pip install websocket-client

ws = websocket.create_connection("ws://127.0.0.1:25570")
ws.send(json.dumps({"type": "hello", "protocol": 1, "token": "K7QM-2XPA", "client": "example"}))
print(json.loads(ws.recv())["state"])
ws.send(json.dumps({"type": "action", "id": 1, "name": "perspective"}))
```
