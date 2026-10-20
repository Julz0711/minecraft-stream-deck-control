package com.julz.streamdeckcontrol.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Collection;

/**
 * Message shapes of the local API, protocol version 1. See docs/protocol.md in the repository.
 */
public final class Protocol {
    public static final int VERSION = 1;

    // Error codes
    public static final String PAIRING_REQUIRED = "pairing_required";
    public static final String UNSUPPORTED_PROTOCOL = "unsupported_protocol";
    public static final String BAD_REQUEST = "bad_request";
    public static final String NOT_PAIRED = "not_paired";
    public static final String UNKNOWN_ACTION = "unknown_action";
    public static final String NOT_IN_WORLD = "not_in_world";
    public static final String NO_PERMISSION = "no_permission";
    public static final String SINGLEPLAYER_ONLY = "singleplayer_only";
    public static final String SCREEN_OPEN = "screen_open";
    public static final String DISABLED = "disabled";
    public static final String INTERNAL = "internal";

    private Protocol() {
    }

    public static JsonObject welcome(String modVersion, String minecraftVersion, Collection<String> actions, JsonObject state) {
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "welcome");
        msg.addProperty("protocol", VERSION);
        msg.addProperty("mod", modVersion);
        msg.addProperty("minecraft", minecraftVersion);
        JsonArray list = new JsonArray();
        actions.stream().sorted().forEach(list::add);
        msg.add("actions", list);
        msg.add("state", state);
        return msg;
    }

    public static JsonObject error(String code, String message) {
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "error");
        msg.addProperty("code", code);
        msg.addProperty("message", message);
        return msg;
    }

    public static JsonObject result(String id, ActionResult result) {
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "result");
        msg.addProperty("id", id);
        msg.addProperty("ok", result.ok());
        if (!result.ok()) {
            msg.addProperty("error", result.error());
            msg.addProperty("message", result.message());
        }
        return msg;
    }

    public static JsonObject state(JsonObject changes) {
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "state");
        msg.add("state", changes);
        return msg;
    }
}
