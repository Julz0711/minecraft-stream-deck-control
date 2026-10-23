import type { GameState } from "./minecraft";

/** 0 ticks is 06:00 in Minecraft; one in-game hour is 1000 ticks. */
export function formatClock(state: GameState): string {
	if (!state.time) {
		return "";
	}
	const ticks = ((state.time.timeOfDay % 24000) + 24000) % 24000;
	const minutes = Math.floor(((ticks + 6000) % 24000) * 0.06);
	const hh = String(Math.floor(minutes / 60)).padStart(2, "0");
	const mm = String(minutes % 60).padStart(2, "0");
	return `${hh}:${mm}`;
}

/** Compass heading in degrees (0 = north, 90 = east) from Minecraft's yaw (0 = south, 90 = west). */
export function heading(yaw: number): number {
	return (((yaw + 180) % 360) + 360) % 360;
}

const CARDINALS = ["N", "NE", "E", "SE", "S", "SW", "W", "NW"];

/** The nearest of the eight compass points, e.g. "NE". */
export function cardinal(degrees: number): string {
	return CARDINALS[Math.round((((degrees % 360) + 360) % 360) / 45) % 8]!;
}

/** "minecraft:dark_forest" or "dark_forest" → "Dark Forest". */
export function biomeName(id: string): string {
	const path = id.includes(":") ? id.slice(id.indexOf(":") + 1) : id;
	return path
		.split(/[_/]/)
		.filter(Boolean)
		.map((word) => word[0]!.toUpperCase() + word.slice(1))
		.join(" ");
}
