import type { JsonObject } from "@elgato/utils";
import streamDeck, {
	type DidReceiveSettingsEvent,
	type KeyAction,
	type KeyDownEvent,
	SingletonAction,
	type WillAppearEvent,
	type WillDisappearEvent
} from "@elgato/streamdeck";

import { COLORS, type KeyFace, renderKey, toDataUrl } from "../keys";
import type { ActionResult, MinecraftConnection } from "../minecraft";

/** Everything a key needs to draw itself and act. */
export type KeyContext<S> = {
	settings: S;
	game: MinecraftConnection;
};

const allKeys: GameKey<any>[] = [];

/** Redraws every visible key, e.g. after the game state changed. */
export function redrawAll(): void {
	for (const key of allKeys) {
		void key.redraw();
	}
}

/**
 * Base for all keys: draws the key face from the game state, shows offline and pairing states,
 * and runs the key's action on press.
 */
export abstract class GameKey<S extends JsonObject = JsonObject> extends SingletonAction<S> {
	private readonly lastImage = new Map<string, string>();
	private readonly flashUntil = new Map<string, number>();

	constructor(protected readonly game: MinecraftConnection) {
		super();
		allKeys.push(this);
	}

	/** The face while connected. */
	protected abstract face(ctx: KeyContext<S>): KeyFace;

	/** Runs the key's action. Return a result to show an alert on failure. */
	protected abstract press(ctx: KeyContext<S>, action: KeyAction<S>): Promise<ActionResult | void>;

	/** The face's glyph while the game is not connected. */
	protected offlineFace(ctx: KeyContext<S>): KeyFace {
		const face = this.face(ctx);
		if (this.game.status === "pairing") {
			return { glyph: face.glyph, label: face.label, value: "PAIR", color: COLORS.warn };
		}
		return { glyph: face.glyph, label: face.label, badge: face.badge, disabled: true };
	}

	override async onWillAppear(ev: WillAppearEvent<S>): Promise<void> {
		this.lastImage.delete(ev.action.id);
		await this.draw(ev.action as KeyAction<S>, ev.payload.settings);
	}

	override onWillDisappear(ev: WillDisappearEvent<S>): void {
		this.lastImage.delete(ev.action.id);
	}

	override async onDidReceiveSettings(ev: DidReceiveSettingsEvent<S>): Promise<void> {
		await this.draw(ev.action as KeyAction<S>, ev.payload.settings);
	}

	override async onKeyDown(ev: KeyDownEvent<S>): Promise<void> {
		if (this.game.status !== "connected") {
			await ev.action.showAlert();
			return;
		}
		const result = await this.press({ settings: ev.payload.settings, game: this.game }, ev.action);
		if (result && !result.ok) {
			streamDeck.logger.info(`${this.manifestId}: ${result.error} ${result.message ?? ""}`);
			await ev.action.showAlert();
		}
		await this.draw(ev.action, ev.payload.settings);
	}

	/** Briefly draws the key in white, e.g. when a screenshot was taken. */
	protected async flash(action: KeyAction<S>, settings: S): Promise<void> {
		this.flashUntil.set(action.id, Date.now() + 250);
		await this.draw(action, settings);
		setTimeout(() => void this.draw(action, settings), 300);
	}

	async redraw(): Promise<void> {
		for (const action of this.actions) {
			if (action.isKey()) {
				await this.draw(action, await action.getSettings());
			}
		}
	}

	private async draw(action: KeyAction<S>, settings: S): Promise<void> {
		const ctx = { settings, game: this.game };
		let face = this.game.status === "connected" ? this.face(ctx) : this.offlineFace(ctx);
		if ((this.flashUntil.get(action.id) ?? 0) > Date.now()) {
			face = { ...face, background: COLORS.flash, color: COLORS.background };
		}
		const image = toDataUrl(renderKey(face));
		if (this.lastImage.get(action.id) !== image) {
			this.lastImage.set(action.id, image);
			await action.setImage(image);
		}
	}
}

/** Whether the player may use admin keys (operator, or cheats on in singleplayer). */
export function canCheat(game: MinecraftConnection): boolean {
	return game.state.inWorld === true && game.state.canCheat === true;
}

export function onOff(value: boolean | undefined): string {
	return value ? "ON" : "OFF";
}
