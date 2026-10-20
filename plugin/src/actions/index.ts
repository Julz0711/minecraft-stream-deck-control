import { action, type KeyAction } from "@elgato/streamdeck";
import type { JsonObject } from "@elgato/utils";

import type { GlyphName } from "../glyphs";
import { COLORS, type KeyFace } from "../keys";
import { biomeName, cardinal, formatClock, heading } from "../format";
import type { GameState, MinecraftConnection } from "../minecraft";
import { canCheat, GameKey, type KeyContext, onOff } from "./game-key";

const UUID = "com.julz.minecraft-control";

type NoSettings = JsonObject;

type Direction = { direction?: "up" | "down"; step?: string };

function delta(settings: Direction, fallback = 1): number {
	const step = Number.parseInt(settings.step ?? "", 10);
	const size = step > 0 ? step : fallback;
	return settings.direction === "down" ? -size : size;
}

function badge(settings: Direction): string {
	return settings.direction === "down" ? "−" : "+";
}

function inWorld(game: MinecraftConnection): boolean {
	return game.state.inWorld === true;
}

/** + or − keys for a numeric setting such as render distance. */
abstract class StepKey extends GameKey<Direction> {
	protected abstract readonly glyph: GlyphName;
	protected abstract readonly label: string;
	protected abstract readonly actionName: string;
	protected abstract value(state: GameState): number | undefined;
	protected readonly defaultStep: number = 1;
	protected available(_game: MinecraftConnection): boolean {
		return true;
	}

	protected face({ settings, game }: KeyContext<Direction>): KeyFace {
		const value = this.value(game.state);
		return {
			glyph: this.glyph,
			label: this.label,
			value: value === undefined ? "" : String(value),
			badge: badge(settings),
			disabled: !this.available(game)
		};
	}

	protected press({ settings, game }: KeyContext<Direction>) {
		return game.request(this.actionName, { delta: delta(settings, this.defaultStep) });
	}
}

@action({ UUID: `${UUID}.render-distance` })
export class RenderDistanceKey extends StepKey {
	protected readonly glyph = "renderDistance";
	protected readonly label = "Render";
	protected readonly actionName = "render_distance";
	protected value(state: GameState) {
		return state.renderDistance;
	}
}

@action({ UUID: `${UUID}.simulation-distance` })
export class SimulationDistanceKey extends StepKey {
	protected readonly glyph = "simulationDistance";
	protected readonly label = "Simulation";
	protected readonly actionName = "simulation_distance";
	protected value(state: GameState) {
		return state.simulationDistance;
	}
	protected override available(game: MinecraftConnection) {
		// The option only reaches the integrated server; on multiplayer the server decides.
		return game.state.singleplayer !== false;
	}
}

@action({ UUID: `${UUID}.fov` })
export class FovKey extends StepKey {
	protected readonly glyph = "fov";
	protected readonly label = "FOV";
	protected readonly actionName = "fov";
	protected override readonly defaultStep = 5;
	protected value(state: GameState) {
		return state.fov;
	}
}

type VolumeSettings = { mode?: "up" | "down" | "mute"; step?: string; source?: string };

/** Short key labels for the game's sound categories. */
const SOUND_LABELS: Record<string, string> = {
	master: "Volume",
	music: "Music",
	record: "Note blocks",
	weather: "Weather",
	block: "Blocks",
	hostile: "Hostile",
	neutral: "Friendly",
	player: "Players",
	ambient: "Ambient",
	voice: "Voice"
};

@action({ UUID: `${UUID}.volume` })
export class VolumeKey extends GameKey<VolumeSettings> {
	protected face({ settings, game }: KeyContext<VolumeSettings>): KeyFace {
		const source = settings.source ?? "master";
		const volume = source === "master" ? game.state.volume : game.state.volumes?.[source];
		const muted = source === "master" ? game.state.muted === true : volume === 0;
		return {
			glyph: muted ? "mute" : "volume",
			label: SOUND_LABELS[source] ?? source,
			value: muted ? "MUTED" : volume === undefined ? "" : `${volume}%`,
			badge: settings.mode === "down" ? "−" : settings.mode === "mute" ? undefined : "+",
			color: muted ? COLORS.warn : undefined
		};
	}

	protected press({ settings, game }: KeyContext<VolumeSettings>) {
		const source = settings.source ?? "master";
		if (settings.mode === "mute") {
			return game.request("mute", { source });
		}
		return game.request("volume", { source, delta: delta({ direction: settings.mode === "down" ? "down" : "up", step: settings.step }, 10) });
	}
}

/** Keys that flip something on and off and show which it is. */
abstract class ToggleKey extends GameKey<NoSettings> {
	protected abstract readonly glyph: GlyphName;
	protected abstract readonly label: string;
	protected abstract readonly actionName: string;
	protected abstract isOn(state: GameState): boolean | undefined;
	protected needsWorld = true;

	protected face({ game }: KeyContext<NoSettings>): KeyFace {
		const on = this.isOn(game.state);
		return {
			glyph: this.glyph,
			label: this.label,
			value: on === undefined ? "" : onOff(on),
			color: on ? COLORS.on : undefined,
			disabled: this.needsWorld && !inWorld(game)
		};
	}

	protected press({ game }: KeyContext<NoSettings>) {
		return game.request(this.actionName);
	}
}

@action({ UUID: `${UUID}.hide-hud` })
export class HideHudKey extends ToggleKey {
	protected readonly glyph = "hud";
	protected readonly label = "HUD";
	protected readonly actionName = "hide_hud";
	protected override face(ctx: KeyContext<NoSettings>): KeyFace {
		// "HUD OFF" reads better than "Hide HUD ON"
		const hidden = ctx.game.state.hudHidden;
		return { ...super.face(ctx), value: hidden === undefined ? "" : hidden ? "HIDDEN" : "SHOWN", color: hidden ? COLORS.on : undefined };
	}
	protected isOn(state: GameState) {
		return state.hudHidden;
	}
}

@action({ UUID: `${UUID}.cinematic` })
export class CinematicKey extends ToggleKey {
	protected readonly glyph = "cinematic";
	protected readonly label = "Cinematic";
	protected readonly actionName = "cinematic";
	protected isOn(state: GameState) {
		return state.cinematic;
	}
}

@action({ UUID: `${UUID}.debug-overlay` })
export class DebugOverlayKey extends ToggleKey {
	protected readonly glyph = "debug";
	protected readonly label = "Debug";
	protected readonly actionName = "debug_overlay";
	protected isOn(state: GameState) {
		return state.debugOverlay;
	}
}

@action({ UUID: `${UUID}.fullscreen` })
export class FullscreenKey extends ToggleKey {
	protected readonly glyph = "fullscreen";
	protected readonly label = "Fullscreen";
	protected readonly actionName = "fullscreen";
	protected override needsWorld = false;
	protected isOn(state: GameState) {
		return state.fullscreen;
	}
}

const PERSPECTIVE_LABELS: Record<string, string> = {
	first_person: "1ST",
	third_person_back: "3RD",
	third_person_front: "FRONT"
};

@action({ UUID: `${UUID}.perspective` })
export class PerspectiveKey extends GameKey<NoSettings> {
	protected face({ game }: KeyContext<NoSettings>): KeyFace {
		return {
			glyph: "perspective",
			label: "View",
			value: PERSPECTIVE_LABELS[game.state.perspective ?? ""] ?? "",
			disabled: !inWorld(game)
		};
	}

	protected press({ game }: KeyContext<NoSettings>) {
		return game.request("perspective");
	}
}

@action({ UUID: `${UUID}.screenshot` })
export class ScreenshotKey extends GameKey<NoSettings> {
	protected face(): KeyFace {
		return { glyph: "screenshot", label: "Screenshot" };
	}

	protected async press({ settings, game }: KeyContext<NoSettings>, action: KeyAction<NoSettings>) {
		const result = await game.request("screenshot");
		if (result.ok) {
			await this.flash(action, settings);
		}
		return result;
	}
}

type ScreenSettings = { screen?: "pause" | "chat" | "inventory" };

const SCREEN_GLYPHS: Record<string, GlyphName> = { pause: "pause", chat: "chat", inventory: "inventory" };

@action({ UUID: `${UUID}.open-screen` })
export class OpenScreenKey extends GameKey<ScreenSettings> {
	protected face({ settings, game }: KeyContext<ScreenSettings>): KeyFace {
		const screen = settings.screen ?? "pause";
		return { glyph: SCREEN_GLYPHS[screen] ?? "pause", label: screen, disabled: !inWorld(game) };
	}

	protected press({ settings, game }: KeyContext<ScreenSettings>) {
		return game.request("open_screen", { screen: settings.screen ?? "pause" });
	}
}

type ChatSettings = { message?: string; label?: string };

@action({ UUID: `${UUID}.chat` })
export class ChatKey extends GameKey<ChatSettings> {
	protected face({ settings, game }: KeyContext<ChatSettings>): KeyFace {
		const label = settings.label?.trim() || settings.message?.trim() || "Chat";
		return { glyph: "chat", value: label, disabled: !inWorld(game) || !settings.message?.trim() };
	}

	protected override offlineFace(ctx: KeyContext<ChatSettings>): KeyFace {
		return { ...super.offlineFace(ctx), value: this.face(ctx).value };
	}

	protected async press({ settings, game }: KeyContext<ChatSettings>, action: KeyAction<ChatSettings>) {
		const message = settings.message?.trim();
		if (!message) {
			return { ok: false as const, error: "no_message", message: "Set a message in the key settings" };
		}
		const result = await game.request("chat", { message });
		if (result.ok) {
			await action.showOk();
		}
		return result;
	}
}

type CommandSettings = { command?: string; label?: string; confirm?: boolean };

@action({ UUID: `${UUID}.command` })
export class CommandKey extends GameKey<CommandSettings> {
	private readonly armed = new Map<string, number>();

	protected face({ settings, game }: KeyContext<CommandSettings>): KeyFace {
		const label = settings.label?.trim() || settings.command?.trim().split(/\s+/)[0]?.replace(/^\//, "") || "Command";
		return { glyph: "command", value: label, disabled: !inWorld(game) || !settings.command?.trim() };
	}

	protected override offlineFace(ctx: KeyContext<CommandSettings>): KeyFace {
		return { ...super.offlineFace(ctx), value: this.face(ctx).value };
	}

	protected async press({ settings, game }: KeyContext<CommandSettings>, action: KeyAction<CommandSettings>) {
		const command = settings.command?.trim();
		if (!command) {
			return { ok: false as const, error: "no_command", message: "Set a command in the key settings" };
		}
		if (settings.confirm) {
			// The first press arms the key, a second press within 3 seconds runs the command.
			const armedUntil = this.armed.get(action.id) ?? 0;
			if (armedUntil < Date.now()) {
				this.armed.set(action.id, Date.now() + 3000);
				await action.setTitle("Press\nagain");
				setTimeout(() => void action.setTitle(""), 3000);
				return;
			}
			this.armed.delete(action.id);
			await action.setTitle("");
		}
		const result = await game.request("command", { command });
		if (result.ok) {
			await action.showOk();
		}
		return result;
	}
}

/** Admin keys: greyed out with a lock unless the player is an operator or has cheats on. */
abstract class AdminKey<S extends JsonObject> extends GameKey<S> {
	protected adminFace(face: KeyFace, game: MinecraftConnection): KeyFace {
		if (!inWorld(game)) {
			return { ...face, disabled: true };
		}
		if (!canCheat(game)) {
			return { ...face, glyph: "lock", disabled: true, value: "OP ONLY" };
		}
		return face;
	}
}

type WeatherSettings = { weather?: "clear" | "rain" | "thunder" };

@action({ UUID: `${UUID}.weather` })
export class WeatherKey extends AdminKey<WeatherSettings> {
	protected face({ settings, game }: KeyContext<WeatherSettings>): KeyFace {
		const weather = settings.weather ?? "clear";
		const current = game.state.weather === weather;
		return this.adminFace({ glyph: weather, label: "Weather", value: weather.toUpperCase(), color: current ? COLORS.on : undefined }, game);
	}

	protected press({ settings, game }: KeyContext<WeatherSettings>) {
		return game.request("weather", { weather: settings.weather ?? "clear" });
	}
}

type TimeSettings = { time?: "day" | "noon" | "night" | "midnight" };

@action({ UUID: `${UUID}.time` })
export class TimeKey extends AdminKey<TimeSettings> {
	protected face({ settings, game }: KeyContext<TimeSettings>): KeyFace {
		const time = settings.time ?? "day";
		return this.adminFace({ glyph: time, label: time, value: formatClock(game.state) }, game);
	}

	protected press({ settings, game }: KeyContext<TimeSettings>) {
		return game.request("time", { time: settings.time ?? "day" });
	}
}

@action({ UUID: `${UUID}.daylight-cycle` })
export class DaylightCycleKey extends AdminKey<NoSettings> {
	protected face({ game }: KeyContext<NoSettings>): KeyFace {
		const on = game.state.daylightCycle;
		return this.adminFace(
			{ glyph: "cycle", label: "Daylight", value: on === undefined || on === null ? "" : onOff(on), color: on ? COLORS.on : undefined },
			game
		);
	}

	protected press({ game }: KeyContext<NoSettings>) {
		return game.request("daylight_cycle");
	}
}

type GameModeSettings = { mode?: "cycle" | "survival" | "creative" | "adventure" | "spectator" };

@action({ UUID: `${UUID}.game-mode` })
export class GameModeKey extends AdminKey<GameModeSettings> {
	protected face({ settings, game }: KeyContext<GameModeSettings>): KeyFace {
		const target = settings.mode ?? "cycle";
		const current = game.state.gameMode ?? undefined;
		const glyph: GlyphName = target === "cycle" ? (current ?? "gameMode") : target;
		return this.adminFace(
			{
				glyph,
				label: "Game mode",
				value: (target === "cycle" ? (current ?? "") : target).toUpperCase(),
				color: target !== "cycle" && current === target ? COLORS.on : undefined
			},
			game
		);
	}

	protected press({ settings, game }: KeyContext<GameModeSettings>) {
		const mode = settings.mode ?? "cycle";
		return game.request("game_mode", mode === "cycle" ? {} : { mode });
	}
}

type StatusSettings = { show?: "fps" | "coords" | "time" };

@action({ UUID: `${UUID}.status` })
export class StatusKey extends GameKey<StatusSettings> {
	protected face({ settings, game }: KeyContext<StatusSettings>): KeyFace {
		const show = settings.show ?? "fps";
		const state = game.state;
		switch (show) {
			case "coords": {
				const p = state.position;
				return { glyph: "coords", label: p ? `${p.x} ${p.z}` : "Coords", value: p ? `Y ${p.y}` : "", disabled: !p };
			}
			case "time":
				return { glyph: "clock", label: state.time ? `Day ${state.time.day}` : "Time", value: formatClock(state), disabled: !state.time };
			default:
				return { glyph: "fps", label: "FPS", value: state.fps === undefined ? "" : String(state.fps) };
		}
	}

	// Read-only: pressing does nothing.
	protected async press() {}
}

@action({ UUID: `${UUID}.compass` })
export class CompassKey extends GameKey<NoSettings> {
	protected face({ game }: KeyContext<NoSettings>): KeyFace {
		const { yaw, biome } = game.state;
		if (yaw === undefined || yaw === null) {
			return { glyph: "compass", label: "Compass", disabled: true };
		}
		const facing = heading(yaw);
		return {
			glyph: "compass",
			label: biome ? biomeName(biome) : "Compass",
			value: cardinal(facing),
			// The needle keeps pointing north as you turn; 15° steps keep the key from redrawing on every twitch.
			rotate: -Math.round(facing / 15) * 15
		};
	}

	// Read-only: pressing does nothing.
	protected async press() {}
}

type ServerSettings = { show?: "ping" | "players" | "name" };

@action({ UUID: `${UUID}.server` })
export class ServerKey extends GameKey<ServerSettings> {
	protected face({ settings, game }: KeyContext<ServerSettings>): KeyFace {
		const server = game.state.server;
		if (!server || !inWorld(game)) {
			return { glyph: "server", label: "Server", disabled: true };
		}
		switch (settings.show ?? "ping") {
			case "players":
				return { glyph: "server", label: "Players", value: server.players === undefined ? "" : String(server.players) };
			case "name":
				return { glyph: "server", label: "Server", value: server.name ?? "" };
			default: {
				const ping = server.ping;
				if (ping === undefined) {
					return { glyph: "server", label: "Ping", value: "LOCAL" };
				}
				return { glyph: "server", label: "Ping", value: `${ping}ms`, color: ping < 100 ? COLORS.on : ping >= 250 ? COLORS.warn : undefined };
			}
		}
	}

	// Read-only: pressing does nothing.
	protected async press() {}
}

type DebugShortcut =
	| "chunk_borders"
	| "hitboxes"
	| "advanced_tooltips"
	| "reload_chunks"
	| "reload_resources"
	| "copy_location"
	| "pause_on_lost_focus";

type DebugShortcutSettings = { key?: DebugShortcut };

const DEBUG_SHORTCUTS: Record<DebugShortcut, { glyph: GlyphName; label: string; isOn?: (state: GameState) => boolean | null | undefined; needsWorld: boolean; hiddenByServer?: boolean }> = {
	chunk_borders: { glyph: "chunkBorders", label: "Chunks", isOn: (s) => s.chunkBorders, needsWorld: true, hiddenByServer: true },
	hitboxes: { glyph: "hitboxes", label: "Hitboxes", isOn: (s) => s.hitboxes, needsWorld: true, hiddenByServer: true },
	advanced_tooltips: { glyph: "tooltip", label: "Tooltips", isOn: (s) => s.advancedTooltips, needsWorld: false },
	pause_on_lost_focus: { glyph: "focus", label: "Pause", isOn: (s) => s.pauseOnLostFocus, needsWorld: false },
	reload_chunks: { glyph: "reload", label: "Chunks", needsWorld: true },
	reload_resources: { glyph: "resources", label: "Resources", needsWorld: false },
	copy_location: { glyph: "copy", label: "Location", needsWorld: true, hiddenByServer: true }
};

/** F3 + key shortcuts: chunk borders (G), hitboxes (B), advanced tooltips (H), reload chunks (A) and more. */
@action({ UUID: `${UUID}.debug-shortcut` })
export class DebugShortcutKey extends GameKey<DebugShortcutSettings> {
	protected face({ settings, game }: KeyContext<DebugShortcutSettings>): KeyFace {
		const shortcut = DEBUG_SHORTCUTS[settings.key ?? "chunk_borders"] ?? DEBUG_SHORTCUTS.chunk_borders;
		if (shortcut.needsWorld && !inWorld(game)) {
			return { glyph: shortcut.glyph, label: shortcut.label, disabled: true };
		}
		if (shortcut.hiddenByServer && game.state.reducedDebugInfo === true) {
			return { glyph: "lock", label: shortcut.label, value: "HIDDEN", disabled: true };
		}
		if (!shortcut.isOn) {
			return { glyph: shortcut.glyph, label: shortcut.label, value: settings.key === "copy_location" ? "COPY" : "RELOAD" };
		}
		const on = shortcut.isOn(game.state);
		return { glyph: shortcut.glyph, label: shortcut.label, value: on === undefined || on === null ? "" : onOff(on), color: on ? COLORS.on : undefined };
	}

	protected async press({ settings, game }: KeyContext<DebugShortcutSettings>, action: KeyAction<DebugShortcutSettings>) {
		const key = settings.key ?? "chunk_borders";
		const result = await game.request("debug_key", { key });
		if (result.ok && !DEBUG_SHORTCUTS[key]?.isOn) {
			await action.showOk();
		}
		return result;
	}
}
