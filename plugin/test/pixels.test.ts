import { describe, expect, it } from "vitest";

import { renderKey } from "../src/keys";
import { PIXEL_ICONS } from "../src/pixels";

describe("pixel icons", () => {
	it("are 16 × 16 and only use colours from their palette", () => {
		for (const [name, icon] of Object.entries(PIXEL_ICONS)) {
			expect(icon!.rows, name).toHaveLength(16);
			for (const row of icon!.rows) {
				expect(row, name).toHaveLength(16);
				for (const key of row) {
					if (key !== ".") expect(icon!.palette[key], `${name}: ${key}`).toBeDefined();
				}
			}
		}
	});

	it("turn grey when the key is disabled", () => {
		const svg = renderKey({ glyph: "hud", disabled: true });
		expect(svg).not.toContain("#e02020");
	});
});
