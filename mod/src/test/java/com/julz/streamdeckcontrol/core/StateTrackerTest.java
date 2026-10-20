package com.julz.streamdeckcontrol.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StateTrackerTest {
    private static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    @Test
    void firstSnapshotReportsEverything() {
        StateTracker tracker = new StateTracker();
        assertEquals(json("{\"fps\":60,\"fov\":70}"), tracker.update(json("{\"fps\":60,\"fov\":70}")));
    }

    @Test
    void reportsOnlyChangedKeys() {
        StateTracker tracker = new StateTracker();
        tracker.update(json("{\"fps\":60,\"fov\":70,\"position\":{\"x\":1,\"y\":2,\"z\":3}}"));
        assertEquals(json("{\"fps\":61}"), tracker.update(json("{\"fps\":61,\"fov\":70,\"position\":{\"x\":1,\"y\":2,\"z\":3}}")));
        assertEquals(json("{\"position\":{\"x\":2,\"y\":2,\"z\":3}}"),
                tracker.update(json("{\"fps\":61,\"fov\":70,\"position\":{\"x\":2,\"y\":2,\"z\":3}}")));
        assertTrue(tracker.update(json("{\"fps\":61,\"fov\":70,\"position\":{\"x\":2,\"y\":2,\"z\":3}}")).isEmpty());
    }

    @Test
    void removedKeysBecomeNull() {
        StateTracker tracker = new StateTracker();
        tracker.update(json("{\"fps\":60,\"weather\":\"rain\"}"));
        assertEquals(json("{\"weather\":null}"), tracker.update(json("{\"fps\":60}")));
        assertEquals(json("{\"fps\":60}"), tracker.snapshot());
    }
}
