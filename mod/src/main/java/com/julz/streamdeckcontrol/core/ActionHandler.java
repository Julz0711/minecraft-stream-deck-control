package com.julz.streamdeckcontrol.core;

import com.google.gson.JsonObject;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Runs actions for the server. Implemented by the client side, which runs them on the game thread. */
public interface ActionHandler {
    /** The action names this mod version supports, sent in the welcome message. */
    Set<String> actionNames();

    /**
     * Runs an action. Called on a server thread; the future may complete on any thread.
     *
     * @param args the request's {@code args} object, never null
     */
    CompletableFuture<ActionResult> run(String name, JsonObject args);
}
