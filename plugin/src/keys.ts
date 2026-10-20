import { GLYPHS, type GlyphName } from "./glyphs";
import { PIXEL_ICONS, type PixelIcon } from "./pixels";

export const COLORS = {
	background: "#16181d",
	text: "#f4f4f5",
	muted: "#71717a",
	on: "#5fd068",
	accent: "#58a6ff",
	warn: "#f5a524",
	flash: "#ffffff"
} as const;

export type KeyFace = {
	glyph: GlyphName;
	/** Big text under the icon, e.g. "12" or "ON". */
	value?: string;
	/** Small text at the top, e.g. "RENDER". */
	label?: string;
	/** Glyph and value colour. */
	color?: string;
	/** Draws everything greyed out, for keys that can't be used right now. */
	disabled?: boolean;
	/** A small corner badge, e.g. "+" or "−". */
	badge?: string;
	background?: string;
	/** Turns the icon clockwise by this many degrees, e.g. a compass needle. */
	rotate?: number;
};

const SIZE = 144;

function escape(text: string): string {
	return text.replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]!);
}

/** Shrinks long values so they still fit on the key. */
function valueFontSize(value: string): number {
	if (value.length <= 4) return 34;
	if (value.length <= 6) return 28;
	if (value.length <= 9) return 21;
	return 16;
}

/** Cuts values that wouldn't fit on the key even at the smallest size. */
function fitValue(value: string): string {
	return value.length > 14 ? `${value.slice(0, 13).trimEnd()}…` : value;
}

/** Draws a pixel icon as one rect per run of same-coloured pixels. Disabled icons turn a dim grey. */
export function pixelArt(icon: PixelIcon, disabled = false): string {
	const rects: string[] = [];
	icon.rows.forEach((row, y) => {
		let x = 0;
		while (x < row.length) {
			const key = row[x]!;
			let end = x + 1;
			while (end < row.length && row[end] === key) end++;
			const fill = icon.palette[key];
			if (fill) {
				rects.push(`<rect x="${x}" y="${y}" width="${end - x}" height="1" fill="${disabled ? greyed(fill) : fill}"/>`);
			}
			x = end;
		}
	});
	return rects.join("");
}

/** The colour as a dim grey of the same brightness, for keys that can't be used right now. */
function greyed(hex: string): string {
	const n = Number.parseInt(hex.slice(1), 16);
	const luma = 0.299 * ((n >> 16) & 255) + 0.587 * ((n >> 8) & 255) + 0.114 * (n & 255);
	const v = Math.round(40 + luma * 0.35).toString(16).padStart(2, "0");
	return `#${v}${v}${v}`;
}

/** A red and grey needle over the compass, turned clockwise by `degrees`. */
function compassNeedle(degrees: number, disabled: boolean): string {
	const north = disabled ? greyed("#e03a2f") : "#e03a2f";
	const south = disabled ? greyed("#4a4a4a") : "#4a4a4a";
	return (
		`<g transform="rotate(${Math.round(degrees)} 8 8)" shape-rendering="geometricPrecision">` +
		`<path d="M8 2.2 L9.6 8 H6.4 Z" fill="${north}"/><path d="M6.4 8 H9.6 L8 13.8 Z" fill="${south}"/></g>`
	);
}

/** Renders a key face as an SVG string, 144 × 144 (the @2x key size). */
export function renderKey(face: KeyFace): string {
	const color = face.disabled ? COLORS.muted : (face.color ?? COLORS.text);
	const textColor = face.disabled ? COLORS.muted : (face.color ?? COLORS.text);
	const hasValue = face.value !== undefined && face.value !== "";
	// A small icon in the middle of the key, with the label above and the value below it
	const glyphSize = hasValue || face.label ? 48 : 80;
	const glyphX = (SIZE - glyphSize) / 2;
	const glyphY = (SIZE - glyphSize) / 2 - (hasValue ? 6 : 0);
	const pixels = PIXEL_ICONS[face.glyph];
	const icon = pixels
		? `<svg x="${glyphX}" y="${glyphY}" width="${glyphSize}" height="${glyphSize}" viewBox="0 0 16 16" shape-rendering="crispEdges">` +
			pixelArt(pixels, face.disabled === true) +
			(face.glyph === "compass" ? compassNeedle(face.rotate ?? 0, face.disabled === true) : "") +
			`</svg>`
		: `<svg x="${glyphX}" y="${glyphY}" width="${glyphSize}" height="${glyphSize}" viewBox="0 0 48 48" fill="none" ` +
			`stroke="${color}" color="${color}" stroke-width="3" stroke-linecap="round" stroke-linejoin="round">` +
			(face.rotate ? `<g transform="rotate(${Math.round(face.rotate)} 24 24)">${GLYPHS[face.glyph]}</g>` : GLYPHS[face.glyph]) +
			`</svg>`;
	const parts: string[] = [
		`<svg xmlns="http://www.w3.org/2000/svg" width="${SIZE}" height="${SIZE}" viewBox="0 0 ${SIZE} ${SIZE}">`,
		`<rect width="${SIZE}" height="${SIZE}" fill="${face.background ?? COLORS.background}"/>`,
		icon
	];
	if (face.label) {
		parts.push(
			`<text x="72" y="24" text-anchor="middle" font-family="Arial, Helvetica, sans-serif" font-size="16" ` +
				`font-weight="bold" letter-spacing="1" fill="${COLORS.muted}">${escape(face.label.toUpperCase())}</text>`
		);
	}
	if (hasValue) {
		const value = fitValue(face.value!);
		parts.push(
			`<text x="72" y="130" text-anchor="middle" font-family="Arial, Helvetica, sans-serif" ` +
				`font-size="${valueFontSize(value)}" font-weight="bold" fill="${textColor}">${escape(value)}</text>`
		);
	}
	if (face.badge) {
		parts.push(
			`<circle cx="124" cy="${face.label ? 44 : 20}" r="15" fill="${face.disabled ? COLORS.muted : COLORS.accent}"/>`,
			`<text x="124" y="${face.label ? 52 : 28}" text-anchor="middle" font-family="Arial, Helvetica, sans-serif" ` +
				`font-size="24" font-weight="bold" fill="${COLORS.background}">${escape(face.badge)}</text>`
		);
	}
	parts.push("</svg>");
	return parts.join("");
}

export function toDataUrl(svg: string): string {
	return `data:image/svg+xml;base64,${Buffer.from(svg).toString("base64")}`;
}
