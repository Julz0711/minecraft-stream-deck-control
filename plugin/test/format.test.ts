import { describe, expect, it } from "vitest";

import { biomeName, cardinal, formatClock, heading } from "../src/format";
import { renderKey } from "../src/keys";

describe("formatClock", () => {
	it("maps Minecraft ticks to a 24 hour clock", () => {
		expect(formatClock({ time: { day: 1, timeOfDay: 0 } })).toBe("06:00");
		expect(formatClock({ time: { day: 1, timeOfDay: 6000 } })).toBe("12:00");
		expect(formatClock({ time: { day: 1, timeOfDay: 13000 } })).toBe("19:00");
		expect(formatClock({ time: { day: 1, timeOfDay: 18000 } })).toBe("00:00");
		expect(formatClock({ time: { day: 1, timeOfDay: 23999 } })).toBe("05:59");
	});

	it("is empty outside a world", () => {
		expect(formatClock({})).toBe("");
	});
});

describe("compass", () => {
	it("turns Minecraft yaw into a compass heading", () => {
		expect(heading(0)).toBe(180);
		expect(heading(90)).toBe(270);
		expect(heading(180)).toBe(0);
		expect(heading(-90)).toBe(90);
		expect(heading(-450)).toBe(90);
	});

	it("names the nearest compass point", () => {
		expect(cardinal(0)).toBe("N");
		expect(cardinal(44)).toBe("NE");
		expect(cardinal(90)).toBe("E");
		expect(cardinal(200)).toBe("S");
		expect(cardinal(350)).toBe("N");
	});

	it("makes biome ids readable", () => {
		expect(biomeName("minecraft:dark_forest")).toBe("Dark Forest");
		expect(biomeName("plains")).toBe("Plains");
		expect(biomeName("terralith:alpine/highlands")).toBe("Alpine Highlands");
	});
});

describe("renderKey", () => {
	it("escapes text and draws the value", () => {
		const svg = renderKey({ glyph: "command", value: "<tp & home>" });
		expect(svg).toContain("&lt;tp &amp; home&gt;");
		expect(svg.startsWith("<svg")).toBe(true);
	});
});
