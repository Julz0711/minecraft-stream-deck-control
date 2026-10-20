/**
 * Writes the plugin's static images from the same glyphs the keys use.
 * Run `npm run icons` after changing a glyph; `npm run icons -- --check` fails if the files are out of date.
 */
import { Resvg } from "@resvg/resvg-js";
import { readFileSync, writeFileSync } from "node:fs";
import path from "node:path";

import { GLYPHS, type GlyphName, glyphSvg } from "../src/glyphs";
import { renderKey } from "../src/keys";

const root = path.resolve(import.meta.dirname, "../com.julz.minecraft-control.sdPlugin");
const check = process.argv.includes("--check");
const stale: string[] = [];

function write(file: string, content: string | Buffer): void {
	const target = path.join(root, file);
	if (check) {
		let current: Buffer | undefined;
		try {
			current = readFileSync(target);
		} catch {
			current = undefined;
		}
		if (!current || !current.equals(Buffer.from(content))) {
			stale.push(file);
		}
		return;
	}
	writeFileSync(target, content);
}

// Action list icons and the default key image of every action in the manifest
const manifest = JSON.parse(readFileSync(path.join(root, "manifest.json"), "utf8"));
for (const action of manifest.Actions) {
	const name = path.basename(action.Icon) as GlyphName;
	if (!(name in GLYPHS)) {
		throw new Error(`No glyph called ${name}`);
	}
	write(`imgs/actions/${name}.svg`, glyphSvg(name, 20) + "\n");
	write(`imgs/actions/${name}-key.svg`, renderKey({ glyph: name, disabled: true }) + "\n");
}

write("imgs/plugin/category-icon.svg", glyphSvg("block", 28) + "\n");

// The Marketplace icon has to be a PNG
const logo =
	`<svg xmlns="http://www.w3.org/2000/svg" width="288" height="288" viewBox="0 0 288 288">` +
	`<rect width="288" height="288" rx="64" fill="#3c8527"/>` +
	`<svg x="54" y="54" width="180" height="180" viewBox="0 0 48 48" fill="none" stroke="#ffffff" stroke-width="3" ` +
	`stroke-linecap="round" stroke-linejoin="round">${GLYPHS.block}</svg></svg>`;
for (const [file, size] of [
	["imgs/plugin/marketplace.png", 256],
	["imgs/plugin/marketplace@2x.png", 512]
] as const) {
	const png = new Resvg(logo, { fitTo: { mode: "width", value: size } }).render().asPng();
	write(file, png);
}

if (check && stale.length > 0) {
	console.error(`Out of date, run npm run icons: ${stale.join(", ")}`);
	process.exit(1);
}
