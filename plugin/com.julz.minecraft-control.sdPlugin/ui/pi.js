// Shared property inspector logic: binds inputs to the key's settings (data-setting) or to the plugin-wide
// settings (data-global), and shows the connection status sent by the plugin.
let socket;
let uuid;
let actionUuid;
let settings = {};
let globalSettings = {};

const CONNECTION = `
	<h2>Connection</h2>
	<div class="item">
		<label for="token">Pairing code</label>
		<input type="text" id="token" data-global="token" placeholder="e.g. K7QM-2XPA" spellcheck="false" autocomplete="off" />
	</div>
	<div id="status"></div>
	<div class="item">
		<label for="port">Port</label>
		<input type="text" id="port" data-global="port" placeholder="Automatic" inputmode="numeric" />
	</div>
	<p class="hint">Minecraft shows the pairing code the first time the Stream Deck connects, and in the mod's settings (Mod Menu). Leave the port empty unless you changed it in the mod.</p>`;

function send(event, payload) {
	socket.send(JSON.stringify({ event, context: uuid, action: actionUuid, payload }));
}

function readInput(input) {
	return input.type === "checkbox" ? input.checked : input.value;
}

function writeInput(input, value) {
	if (input.type === "checkbox") {
		input.checked = value === true;
	} else if (value !== undefined && value !== null) {
		input.value = value;
	}
}

function fill() {
	for (const input of document.querySelectorAll("[data-setting]")) {
		writeInput(input, settings[input.dataset.setting] ?? input.dataset.default);
	}
	for (const input of document.querySelectorAll("[data-global]")) {
		writeInput(input, globalSettings[input.dataset.global]);
	}
}

function bind() {
	for (const input of document.querySelectorAll("[data-setting]")) {
		input.addEventListener(input.tagName === "SELECT" || input.type === "checkbox" ? "change" : "input", () => {
			settings[input.dataset.setting] = readInput(input);
			send("setSettings", settings);
		});
	}
	for (const input of document.querySelectorAll("[data-global]")) {
		input.addEventListener("change", () => {
			globalSettings[input.dataset.global] = readInput(input).trim();
			send("setGlobalSettings", globalSettings);
		});
	}
}

// Called by the Stream Deck app when the property inspector opens.
window.connectElgatoStreamDeckSocket = (port, inUuid, registerEvent, _info, actionInfo) => {
	uuid = inUuid;
	const info = JSON.parse(actionInfo);
	actionUuid = info.action;
	settings = info.payload?.settings ?? {};

	document.body.insertAdjacentHTML("beforeend", CONNECTION);
	fill();
	bind();

	socket = new WebSocket(`ws://127.0.0.1:${port}`);
	socket.onopen = () => {
		socket.send(JSON.stringify({ event: registerEvent, uuid }));
		socket.send(JSON.stringify({ event: "getGlobalSettings", context: uuid }));
		send("sendToPlugin", { event: "getStatus" });
	};
	socket.onmessage = (message) => {
		const data = JSON.parse(message.data);
		if (data.event === "didReceiveGlobalSettings") {
			globalSettings = data.payload?.settings ?? {};
			fill();
		} else if (data.event === "didReceiveSettings") {
			settings = data.payload?.settings ?? {};
			fill();
		} else if (data.event === "sendToPropertyInspector" && data.payload?.event === "status") {
			const status = document.getElementById("status");
			status.textContent = data.payload.text;
			status.dataset.status = data.payload.status;
		}
	};
};
