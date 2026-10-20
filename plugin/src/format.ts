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
