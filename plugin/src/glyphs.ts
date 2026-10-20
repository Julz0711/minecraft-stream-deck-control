/**
 * Line icons drawn in a 48 × 48 box, stroked with `currentColor`. Used for the key faces and,
 * via scripts/make-icons.mjs, for the icons in the Stream Deck action list.
 */
export const GLYPHS = {
	// Two hills with a far horizon line
	renderDistance: `<path d="M4 38 L16 22 L24 32 L32 18 L44 38 Z"/><path d="M4 10 H44" stroke-dasharray="4 4"/>`,
	// A chunk grid
	simulationDistance: `<rect x="6" y="6" width="36" height="36" rx="2"/><path d="M18 6 V42 M30 6 V42 M6 18 H42 M6 30 H42"/>`,
	// An eye
	perspective: `<path d="M4 24 C12 12 36 12 44 24 C36 36 12 36 4 24 Z"/><circle cx="24" cy="24" r="6"/>`,
	// A HUD frame with a slash
	hud: `<rect x="6" y="10" width="36" height="28" rx="3"/><path d="M12 32 H24 M30 32 H36"/>`,
	// A film strip with letterbox bars
	cinematic: `<rect x="4" y="8" width="40" height="32" rx="3"/><path d="M4 15 H44 M4 33 H44"/><path d="M21 20 L29 24 L21 28 Z" fill="currentColor"/>`,
	// A camera
	screenshot: `<path d="M6 16 H14 L18 10 H30 L34 16 H42 V38 H6 Z"/><circle cx="24" cy="26" r="7"/>`,
	// Text lines like the F3 screen
	debug: `<path d="M6 10 H30 M6 18 H38 M6 26 H24 M6 34 H34"/>`,
	// A field of view cone
	fov: `<path d="M24 40 L6 10 M24 40 L42 10"/><path d="M10 16 Q24 6 38 16"/>`,
	// A speaker
	volume: `<path d="M6 18 H14 L24 9 V39 L14 30 H6 Z"/><path d="M30 17 Q35 24 30 31 M35 12 Q44 24 35 36"/>`,
	mute: `<path d="M6 18 H14 L24 9 V39 L14 30 H6 Z"/><path d="M31 18 L43 30 M43 18 L31 30"/>`,
	// Arrows to the four corners
	fullscreen: `<path d="M6 16 V6 H16 M32 6 H42 V16 M42 32 V42 H32 M16 42 H6 V32"/>`,
	// Two pause bars
	pause: `<path d="M17 10 V38 M31 10 V38" stroke-width="6"/>`,
	// A speech bubble
	chat: `<path d="M6 8 H42 V32 H20 L10 40 V32 H6 Z"/>`,
	// A chest
	inventory: `<rect x="6" y="12" width="36" height="28" rx="2"/><path d="M6 22 H42"/><rect x="21" y="19" width="6" height="7"/>`,
	// A slash command prompt
	command: `<path d="M8 12 L18 24 L8 36"/><path d="M22 36 H40"/>`,
	// Weather
	clear: `<circle cx="24" cy="24" r="8"/><path d="M24 4 V10 M24 38 V44 M4 24 H10 M38 24 H44 M10 10 L14 14 M34 34 L38 38 M38 10 L34 14 M14 34 L10 38"/>`,
	rain: `<path d="M12 26 A8 8 0 0 1 14 10 A11 11 0 0 1 34 10 A8 8 0 0 1 36 26 Z"/><path d="M14 32 L12 40 M24 32 L22 40 M34 32 L32 40"/>`,
	thunder: `<path d="M12 24 A8 8 0 0 1 14 8 A11 11 0 0 1 34 8 A8 8 0 0 1 36 24 Z"/><path d="M26 26 L18 36 H26 L22 44" />`,
	// Time
	day: `<circle cx="24" cy="28" r="8"/><path d="M4 38 H44 M24 10 V14 M10 18 L13 21 M38 18 L35 21"/>`,
	noon: `<circle cx="24" cy="24" r="10"/><path d="M24 4 V8 M24 40 V44 M4 24 H8 M40 24 H44"/>`,
	night: `<path d="M30 6 A16 16 0 1 0 42 30 A12 12 0 1 1 30 6 Z"/>`,
	midnight: `<path d="M30 6 A16 16 0 1 0 42 30 A12 12 0 1 1 30 6 Z"/><path d="M38 8 L40 12 M12 40 L14 42" />`,
	// A clock face
	clock: `<circle cx="24" cy="24" r="18"/><path d="M24 12 V24 L32 30"/>`,
	cycle: `<path d="M38 18 A15 15 0 0 0 10 20 M10 30 A15 15 0 0 0 38 28"/><path d="M38 10 V18 H30 M10 38 V30 H18"/>`,
	// Game modes
	survival: `<path d="M24 6 L40 12 V24 C40 34 32 40 24 43 C16 40 8 34 8 24 V12 Z"/>`,
	creative: `<path d="M24 4 L42 14 V34 L24 44 L6 34 V14 Z M6 14 L24 24 L42 14 M24 24 V44"/>`,
	adventure: `<path d="M8 40 L36 12 M30 10 L38 18 M12 30 L18 36 M8 40 L12 36"/>`,
	spectator: `<path d="M4 24 C12 12 36 12 44 24 C36 36 12 36 4 24 Z"/><circle cx="24" cy="24" r="6"/><path d="M8 40 L40 8"/>`,
	gameMode: `<path d="M24 4 L42 14 V34 L24 44 L6 34 V14 Z"/><path d="M18 24 H30 M24 18 V30"/>`,
	// Status
	fps: `<path d="M6 40 V8 M6 40 H42"/><path d="M10 32 L18 22 L26 28 L40 12"/>`,
	coords: `<circle cx="24" cy="24" r="16"/><path d="M24 4 V44 M4 24 H44"/><circle cx="24" cy="24" r="3" fill="currentColor"/>`,
	plus: `<path d="M24 10 V38 M10 24 H38"/>`,
	minus: `<path d="M10 24 H38"/>`,
	lock: `<rect x="10" y="22" width="28" height="20" rx="3"/><path d="M16 22 V14 A8 8 0 0 1 32 14 V22"/>`,
	// The plugin's own mark: a grass block
	block: `<path d="M24 4 L42 14 V34 L24 44 L6 34 V14 Z M6 14 L24 24 L42 14 M24 24 V44"/><path d="M6 19 L24 29 L42 19"/>`
} as const;

export type GlyphName = keyof typeof GLYPHS;

/** A standalone SVG of one glyph, for static icon files. */
export function glyphSvg(name: GlyphName, size: number, color = "#ffffff"): string {
	return (
		`<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 48 48" fill="none" ` +
		`stroke="${color}" color="${color}" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">${GLYPHS[name]}</svg>`
	);
}
