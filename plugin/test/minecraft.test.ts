import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { WebSocketServer, type WebSocket } from "ws";

import { MinecraftConnection } from "../src/minecraft";

/** A tiny stand-in for the mod's API. */
class FakeGame {
	server: WebSocketServer;
	sockets: WebSocket[] = [];
	actions: { name: string; args: unknown }[] = [];
	code = "K7QM-2XPA";
	answer: (name: string) => object = () => ({ ok: true });

	constructor(readonly port: number) {
		this.server = new WebSocketServer({ host: "127.0.0.1", port });
		this.server.on("connection", (socket) => {
			this.sockets.push(socket);
			socket.on("message", (data) => {
				const msg = JSON.parse(data.toString());
				if (msg.type === "hello") {
					if (msg.token !== this.code) {
						socket.send(JSON.stringify({ type: "error", code: "pairing_required" }));
						socket.close(4001);
						return;
					}
					socket.send(
						JSON.stringify({ type: "welcome", protocol: 1, mod: "1.0.0", actions: ["fov", "perspective"], state: { fov: 70, inWorld: true } })
					);
				} else if (msg.type === "action") {
					this.actions.push({ name: msg.name, args: msg.args });
					socket.send(JSON.stringify({ type: "result", id: msg.id, ...this.answer(msg.name) }));
				}
			});
		});
	}

	push(state: object): void {
		for (const socket of this.sockets) {
			socket.send(JSON.stringify({ type: "state", state }));
		}
	}

	close(): Promise<void> {
		for (const socket of this.sockets) {
			socket.terminate();
		}
		return new Promise((resolve) => this.server.close(() => resolve()));
	}
}

function waitFor(predicate: () => boolean, timeoutMs = 3000): Promise<void> {
	return new Promise((resolve, reject) => {
		const started = Date.now();
		const timer = setInterval(() => {
			if (predicate()) {
				clearInterval(timer);
				resolve();
			} else if (Date.now() - started > timeoutMs) {
				clearInterval(timer);
				reject(new Error("timed out"));
			}
		}, 10);
	});
}

let port = 41570;
let game: FakeGame;
let connection: MinecraftConnection;

beforeEach(() => {
	port += 1;
	game = new FakeGame(port);
	connection = new MinecraftConnection("test", 50, 500);
});

afterEach(async () => {
	connection.stop();
	await game.close();
});

describe("MinecraftConnection", () => {
	it("connects with the pairing code and keeps the state", async () => {
		connection.configure({ token: game.code, port: String(port) });
		connection.start();
		await waitFor(() => connection.status === "connected");
		expect(connection.state).toEqual({ fov: 70, inWorld: true });
		expect([...connection.actions]).toEqual(["fov", "perspective"]);

		const changes: object[] = [];
		connection.on("state", (c) => changes.push(c));
		game.push({ fov: 90, weather: "rain" });
		await waitFor(() => connection.state.fov === 90);
		game.push({ weather: null });
		await waitFor(() => !("weather" in connection.state));
		expect(changes).toEqual([{ fov: 90, weather: "rain" }, { weather: null }]);
	});

	it("waits for pairing and connects once the code is entered", async () => {
		connection.configure({ token: "WRONG", port: String(port) });
		connection.start();
		await waitFor(() => connection.status === "pairing");
		connection.configure({ token: game.code, port: String(port) });
		await waitFor(() => connection.status === "connected");
	});

	it("runs actions and reports results", async () => {
		connection.configure({ token: game.code, port: String(port) });
		connection.start();
		await waitFor(() => connection.status === "connected");

		expect(await connection.request("fov", { delta: 5 })).toEqual({ ok: true });
		expect(game.actions).toEqual([{ name: "fov", args: { delta: 5 } }]);

		game.answer = () => ({ ok: false, error: "no_permission", message: "nope" });
		expect(await connection.request("perspective")).toEqual({ ok: false, error: "no_permission", message: "nope" });

		// Actions the mod doesn't offer are not sent at all
		expect((await connection.request("fly")).ok).toBe(false);
		expect(game.actions).toHaveLength(2);
	});

	it("reports offline and reconnects after the game closes", async () => {
		connection.configure({ token: game.code, port: String(port) });
		connection.start();
		await waitFor(() => connection.status === "connected");
		await game.close();
		await waitFor(() => connection.status === "searching");
		expect(connection.state).toEqual({});
		expect((await connection.request("fov")).ok).toBe(false);

		game = new FakeGame(port);
		await waitFor(() => connection.status === "connected");
	});
});
