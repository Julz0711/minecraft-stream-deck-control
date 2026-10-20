import streamDeck from "@elgato/streamdeck";

import {
	CinematicKey,
	CommandKey,
	DaylightCycleKey,
	DebugOverlayKey,
	FovKey,
	FullscreenKey,
	GameModeKey,
	HideHudKey,
	OpenScreenKey,
	PerspectiveKey,
	RenderDistanceKey,
	ScreenshotKey,
	SimulationDistanceKey,
	StatusKey,
	TimeKey,
	VolumeKey,
	WeatherKey
} from "./actions";
import { redrawAll } from "./actions/game-key";
import { type ConnectionSettings, MinecraftConnection } from "./minecraft";

const game = new MinecraftConnection(`Stream Deck plugin ${streamDeck.info.plugin.version}`);

for (const key of [
	RenderDistanceKey,
	SimulationDistanceKey,
	PerspectiveKey,
	HideHudKey,
	CinematicKey,
	ScreenshotKey,
	DebugOverlayKey,
	FovKey,
	VolumeKey,
	FullscreenKey,
	OpenScreenKey,
	CommandKey,
	WeatherKey,
	TimeKey,
	DaylightCycleKey,
	GameModeKey,
	StatusKey
]) {
	streamDeck.actions.registerAction(new key(game));
}

/** What the settings panel shows under "Connection". */
function connectionStatus(): string {
	switch (game.status) {
		case "connected":
			return `Connected to Minecraft (port ${game.port}, mod ${game.modVersion})`;
		case "pairing":
			return "Minecraft found. Enter the pairing code shown in the game.";
		default:
			return "Looking for Minecraft. Start the game with the Stream Deck Control mod.";
	}
}

function sendStatus(): void {
	void streamDeck.ui.sendToPropertyInspector({ event: "status", status: game.status, text: connectionStatus() });
}

// Redraw keys at most every 50 ms, since FPS and position update constantly.
let redrawQueued = false;
function queueRedraw(): void {
	if (!redrawQueued) {
		redrawQueued = true;
		setTimeout(() => {
			redrawQueued = false;
			redrawAll();
		}, 50);
	}
}

game.on("state", queueRedraw);
game.on("status", (status) => {
	streamDeck.logger.info(`Minecraft connection: ${status}`);
	queueRedraw();
	sendStatus();
});

streamDeck.settings.onDidReceiveGlobalSettings<ConnectionSettings>((ev) => game.configure(ev.settings));
streamDeck.ui.onSendToPlugin((ev) => {
	if ((ev.payload as { event?: string })?.event === "getStatus") {
		sendStatus();
	}
});

await streamDeck.connect();
game.configure(await streamDeck.settings.getGlobalSettings<ConnectionSettings>());
game.start();
