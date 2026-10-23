import { EventEmitter } from "node:events";
import WebSocket from "ws";

/** Protocol version this plugin speaks. See docs/protocol.md in the repository. */
export const PROTOCOL_VERSION = 1;
/** The mod takes the first free port from here, so a second game instance gets the next one. */
export const FIRST_PORT = 25570;
export const PORT_RANGE = 10;

export type Status =
	/** No game found yet. */
	| "searching"
	/** A game answered but wants the pairing code. */
	| "pairing"
	| "connected";

export type GameState = Record<string, unknown> & {
	inWorld?: boolean;
	singleplayer?: boolean;
	canCheat?: boolean;
	renderDistance?: number;
	simulationDistance?: number;
	perspective?: "first_person" | "third_person_back" | "third_person_front";
	hudHidden?: boolean;
	cinematic?: boolean;
	debugOverlay?: boolean;
	fullscreen?: boolean;
	fov?: number;
	volume?: number;
	muted?: boolean;
	/** Percent per sound category, e.g. { master: 100, record: 40 }. */
	volumes?: Record<string, number>;
	fps?: number;
	position?: { x: number; y: number; z: number } | null;
	time?: { day: number; timeOfDay: number } | null;
	weather?: "clear" | "rain" | "thunder" | null;
	daylightCycle?: boolean | null;
	advancedTooltips?: boolean;
	pauseOnLostFocus?: boolean;
	chunkBorders?: boolean | null;
	hitboxes?: boolean | null;
	reducedDebugInfo?: boolean | null;
	/** Minecraft yaw in whole degrees, 0 to 359 (0 = south). */
	yaw?: number | null;
	/** Biome id, e.g. "minecraft:plains". */
	biome?: string | null;
	server?: { name?: string; players?: number; ping?: number } | null;
	gameMode?: "survival" | "creative" | "adventure" | "spectator" | null;
};

export type ActionResult = { ok: true } | { ok: false; error: string; message?: string };

export type ConnectionSettings = {
	token?: string;
	/** A port number, or empty for automatic. */
	port?: string;
};

type Pending = { resolve: (result: ActionResult) => void; timer: NodeJS.Timeout };

/**
 * Finds the game on localhost, pairs with the code from Minecraft, keeps the latest game state and runs actions.
 *
 * Events: `status` (Status), `state` (changed keys), `welcome`.
 */
export class MinecraftConnection extends EventEmitter {
	status: Status = "searching";
	state: GameState = {};
	actions = new Set<string>();
	modVersion?: string;
	port?: number;

	private settings: ConnectionSettings = {};
	private socket?: WebSocket;
	private nextId = 1;
	private pending = new Map<string, Pending>();
	private retryTimer?: NodeJS.Timeout;
	private scanning = false;
	private stopped = true;

	constructor(
		private readonly clientName: string,
		private readonly retryMs = 3000,
		private readonly requestTimeoutMs = 3000,
		private readonly host = "127.0.0.1"
	) {
		super();
	}

	start(): void {
		this.stopped = false;
		this.scheduleRetry(0);
	}

	stop(): void {
		this.stopped = true;
		clearTimeout(this.retryTimer);
		this.socket?.close();
	}

	/** Applies new global settings; reconnects when the code or port changed. */
	configure(settings: ConnectionSettings): void {
		const changed = settings.token !== this.settings.token || settings.port !== this.settings.port;
		this.settings = { ...settings };
		if (changed && !this.stopped && this.status !== "connected") {
			this.scheduleRetry(0);
		} else if (changed && this.status === "connected" && settings.port && Number(settings.port) !== this.port) {
			this.socket?.close();
		}
	}

	/** Runs an action in the game. Resolves with an error result instead of rejecting. */
	request(name: string, args: Record<string, unknown> = {}): Promise<ActionResult> {
		const socket = this.socket;
		if (this.status !== "connected" || !socket) {
			return Promise.resolve({ ok: false, error: "offline", message: "Minecraft is not connected" });
		}
		if (!this.actions.has(name)) {
			return Promise.resolve({ ok: false, error: "unknown_action", message: "Update the mod to use this key" });
		}
		const id = String(this.nextId++);
		return new Promise((resolve) => {
			const timer = setTimeout(() => {
				this.pending.delete(id);
				resolve({ ok: false, error: "timeout", message: "Minecraft did not answer" });
			}, this.requestTimeoutMs);
			this.pending.set(id, { resolve, timer });
			socket.send(JSON.stringify({ type: "action", id, name, args }));
		});
	}

	private ports(): number[] {
		const fixed = Number.parseInt(this.settings.port ?? "", 10);
		if (fixed > 0 && fixed < 65536) {
			return [fixed];
		}
		return Array.from({ length: PORT_RANGE }, (_, i) => FIRST_PORT + i);
	}

	private scheduleRetry(delay: number): void {
		clearTimeout(this.retryTimer);
		if (!this.stopped) {
			this.retryTimer = setTimeout(() => void this.scan(), delay);
		}
	}

	private async scan(): Promise<void> {
		if (this.scanning || this.stopped || this.status === "connected") {
			return;
		}
		this.scanning = true;
		let pairing = false;
		try {
			for (const port of this.ports()) {
				const outcome = await this.tryPort(port);
				if (outcome === "connected") {
					return;
				}
				pairing ||= outcome === "pairing";
			}
			this.setStatus(pairing ? "pairing" : "searching");
		} finally {
			this.scanning = false;
		}
		this.scheduleRetry(this.retryMs);
	}

	/** Connects to one port and sends hello. Keeps the socket if the game welcomes us. */
	private tryPort(port: number): Promise<"connected" | "pairing" | "none"> {
		return new Promise((resolve) => {
			let settled = false;
			const settle = (outcome: "connected" | "pairing" | "none") => {
				if (!settled) {
					settled = true;
					clearTimeout(timeout);
					resolve(outcome);
				}
			};
			const socket = new WebSocket(`ws://${this.host}:${port}`, { handshakeTimeout: 1000 });
			const timeout = setTimeout(() => {
				socket.terminate();
				settle("none");
			}, 2000);

			socket.on("open", () => {
				socket.send(
					JSON.stringify({ type: "hello", protocol: PROTOCOL_VERSION, token: this.settings.token ?? "", client: this.clientName })
				);
			});
			socket.on("message", (data) => {
				let msg: any;
				try {
					msg = JSON.parse(data.toString());
				} catch {
					return;
				}
				if (!settled) {
					if (msg.type === "welcome") {
						this.adopt(socket, port, msg);
						settle("connected");
					} else if (msg.type === "error") {
						socket.close();
						settle(msg.code === "pairing_required" ? "pairing" : "none");
					}
					return;
				}
				this.handle(msg);
			});
			socket.on("error", () => settle("none"));
			socket.on("close", () => {
				settle("none");
				if (this.socket === socket) {
					this.lost();
				}
			});
		});
	}

	private adopt(socket: WebSocket, port: number, welcome: any): void {
		this.socket = socket;
		this.port = port;
		this.modVersion = welcome.mod;
		this.actions = new Set(Array.isArray(welcome.actions) ? welcome.actions : []);
		this.state = { ...(welcome.state ?? {}) };
		this.setStatus("connected");
		this.emit("welcome");
		this.emit("state", { ...this.state });
	}

	private handle(msg: any): void {
		if (msg.type === "state" && msg.state && typeof msg.state === "object") {
			for (const [key, value] of Object.entries(msg.state)) {
				if (value === null) {
					delete this.state[key];
				} else {
					this.state[key] = value;
				}
			}
			this.emit("state", msg.state);
		} else if (msg.type === "result") {
			const pending = this.pending.get(String(msg.id));
			if (pending) {
				clearTimeout(pending.timer);
				this.pending.delete(String(msg.id));
				pending.resolve(msg.ok ? { ok: true } : { ok: false, error: msg.error ?? "failed", message: msg.message });
			}
		}
	}

	private lost(): void {
		this.socket = undefined;
		this.state = {};
		for (const [id, pending] of this.pending) {
			clearTimeout(pending.timer);
			pending.resolve({ ok: false, error: "offline", message: "Minecraft closed the connection" });
			this.pending.delete(id);
		}
		this.setStatus("searching");
		this.emit("state", {});
		this.scheduleRetry(this.retryMs);
	}

	private setStatus(status: Status): void {
		if (this.status !== status) {
			this.status = status;
			this.emit("status", status);
		}
	}
}
