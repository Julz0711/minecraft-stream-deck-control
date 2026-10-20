package com.julz.streamdeckcontrol.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import java.util.Map;
import java.util.Objects;

/** Remembers the last state sent to clients and works out which top-level keys changed. */
public class StateTracker {
    private JsonObject current = new JsonObject();

    /**
     * Stores a new snapshot and returns the keys that differ from the previous one, or an empty object
     * if nothing changed. A key missing from the new snapshot is reported as null.
     */
    public synchronized JsonObject update(JsonObject snapshot) {
        JsonObject changes = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : snapshot.entrySet()) {
            if (!Objects.equals(current.get(entry.getKey()), entry.getValue())) {
                changes.add(entry.getKey(), entry.getValue());
            }
        }
        for (String key : current.keySet()) {
            if (!snapshot.has(key)) {
                changes.add(key, JsonNull.INSTANCE);
            }
        }
        current = snapshot.deepCopy();
        return changes;
    }

    /** The full last snapshot, for new clients. */
    public synchronized JsonObject snapshot() {
        return current.deepCopy();
    }
}
