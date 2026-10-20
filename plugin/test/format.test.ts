import { describe, expect, it } from "vitest";

import { formatClock } from "../src/format";
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

describe("renderKey", () => {
	it("escapes text and draws the value", () => {
		const svg = renderKey({ glyph: "command", value: "<tp & home>" });
		expect(svg).toContain("&lt;tp &amp; home&gt;");
		expect(svg.startsWith("<svg")).toBe(true);
	});
});
