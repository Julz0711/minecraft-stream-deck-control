package com.julz.streamdeckcontrol.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiServerTest {
    private static final String TOKEN = "K7QM-2XPA";

    private final List<String> pairingRequests = new CopyOnWriteArrayList<>();
    private final List<String> ranActions = new CopyOnWriteArrayList<>();
    private ApiServer server;
    private int port;

    private final ActionHandler handler = new ActionHandler() {
        @Override
        public Set<String> actionNames() {
            return Set.of("fov", "boom");
        }

        @Override
        public CompletableFuture<ActionResult> run(String name, JsonObject args) {
            ranActions.add(name + args);
            if (name.equals("boom")) {
                return CompletableFuture.failedFuture(new IllegalStateException("boom"));
            }
            if (args.has("delta") && args.get("delta").getAsInt() > 10) {
                return CompletableFuture.completedFuture(ActionResult.fail(Protocol.NO_PERMISSION, "nope"));
            }
            return CompletableFuture.completedFuture(ActionResult.OK);
        }
    };

    @BeforeEach
    void start() {
        server = new ApiServer(
                new ApiServer.Settings(() -> TOKEN, () -> List.of("https://allowed.example"), "1.0.0", "26.3"),
                handler, pairingRequests::add);
        port = server.start(31570, 20);
        assertTrue(port > 0, "server bound");
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    @Test
    void helloWithTokenGetsWelcomeWithActionsAndState() throws Exception {
        server.publishState(json("{\"fov\":70}"));
        try (TestClient client = connect(Map.of())) {
            client.send("{\"type\":\"hello\",\"protocol\":1,\"token\":\"k7qm2xpa\",\"client\":\"test\"}");
            JsonObject welcome = client.next();
            assertEquals("welcome", welcome.get("type").getAsString());
            assertEquals(1, welcome.get("protocol").getAsInt());
            assertEquals("[\"boom\",\"fov\"]", welcome.get("actions").toString());
            assertEquals(json("{\"fov\":70}"), welcome.getAsJsonObject("state"));
            assertEquals(1, server.clientCount());
        }
    }

    @Test
    void wrongTokenAsksForPairingAndCloses() throws Exception {
        try (TestClient client = connect(Map.of())) {
            client.send("{\"type\":\"hello\",\"protocol\":1,\"token\":\"WRONG\",\"client\":\"Stream Deck\"}");
            assertEquals(Protocol.PAIRING_REQUIRED, client.next().get("code").getAsString());
            assertEquals(ApiServer.CLOSE_PAIRING_REQUIRED, client.closeCode.get(5, TimeUnit.SECONDS));
            assertEquals(List.of("Stream Deck"), pairingRequests);
        }
    }

    @Test
    void otherProtocolVersionIsRejected() throws Exception {
        try (TestClient client = connect(Map.of())) {
            client.send("{\"type\":\"hello\",\"protocol\":2,\"token\":\"" + TOKEN + "\"}");
            assertEquals(Protocol.UNSUPPORTED_PROTOCOL, client.next().get("code").getAsString());
            assertEquals(ApiServer.CLOSE_UNSUPPORTED_PROTOCOL, client.closeCode.get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void actionsNeedPairingFirst() throws Exception {
        try (TestClient client = connect(Map.of())) {
            client.send("{\"type\":\"action\",\"id\":\"1\",\"name\":\"fov\"}");
            assertEquals(Protocol.NOT_PAIRED, client.next().get("code").getAsString());
            assertTrue(ranActions.isEmpty());
        }
    }

    @Test
    void browserPagesAreRejectedUnlessAllowListed() throws Exception {
        TestClient blocked = new TestClient(URI.create("ws://127.0.0.1:" + port), Map.of("Origin", "https://evil.example"));
        assertFalse(blocked.connectBlocking(5, TimeUnit.SECONDS) && blocked.isOpen());
        blocked.close();

        try (TestClient allowed = connect(Map.of("Origin", "https://allowed.example"))) {
            assertTrue(allowed.isOpen());
        }
    }

    @Test
    void runsActionsAndReportsResults() throws Exception {
        try (TestClient client = paired()) {
            client.send("{\"type\":\"action\",\"id\":7,\"name\":\"fov\",\"args\":{\"delta\":1}}");
            assertEquals(json("{\"type\":\"result\",\"id\":\"7\",\"ok\":true}"), client.next());
            assertEquals(List.of("fov{\"delta\":1}"), ranActions);

            client.send("{\"type\":\"action\",\"id\":\"8\",\"name\":\"fov\",\"args\":{\"delta\":50}}");
            JsonObject failed = client.next();
            assertFalse(failed.get("ok").getAsBoolean());
            assertEquals(Protocol.NO_PERMISSION, failed.get("error").getAsString());

            client.send("{\"type\":\"action\",\"id\":\"9\",\"name\":\"boom\"}");
            assertEquals(Protocol.INTERNAL, client.next().get("error").getAsString());

            client.send("{\"type\":\"action\",\"id\":\"10\",\"name\":\"fly\"}");
            assertEquals(Protocol.UNKNOWN_ACTION, client.next().get("error").getAsString());

            client.send("not json");
            assertEquals(Protocol.BAD_REQUEST, client.next().get("code").getAsString());
        }
    }

    @Test
    void pushesOnlyChangedState() throws Exception {
        server.publishState(json("{\"fov\":70,\"fps\":60}"));
        try (TestClient client = paired()) {
            server.publishState(json("{\"fov\":70,\"fps\":61}"));
            assertEquals(json("{\"type\":\"state\",\"state\":{\"fps\":61}}"), client.next());
            server.publishState(json("{\"fov\":70,\"fps\":61}"));
            server.publishState(json("{\"fov\":80,\"fps\":61}"));
            assertEquals(json("{\"type\":\"state\",\"state\":{\"fov\":80}}"), client.next());
        }
    }

    @Test
    void secondServerTakesTheNextPort() {
        ApiServer second = new ApiServer(
                new ApiServer.Settings(() -> TOKEN, List::of, "1.0.0", "26.3"), handler, pairingRequests::add);
        try {
            int secondPort = second.start(port, 5);
            assertTrue(secondPort > port, "second port " + secondPort);
            assertNotEquals(port, secondPort);
        } finally {
            second.stop();
        }
    }

    private TestClient paired() throws Exception {
        TestClient client = connect(Map.of());
        client.send("{\"type\":\"hello\",\"protocol\":1,\"token\":\"" + TOKEN + "\"}");
        assertEquals("welcome", client.next().get("type").getAsString());
        return client;
    }

    private TestClient connect(Map<String, String> headers) throws Exception {
        TestClient client = new TestClient(URI.create("ws://127.0.0.1:" + port), headers);
        assertTrue(client.connectBlocking(5, TimeUnit.SECONDS), "connected");
        return client;
    }

    private static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    private static class TestClient extends WebSocketClient implements AutoCloseable {
        final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        final CompletableFuture<Integer> closeCode = new CompletableFuture<>();

        TestClient(URI uri, Map<String, String> headers) {
            super(uri, headers);
        }

        JsonObject next() throws InterruptedException {
            String message = messages.poll(5, TimeUnit.SECONDS);
            assertNotNull(message, "expected a message");
            return json(message);
        }

        @Override
        public void onOpen(ServerHandshake handshake) {
        }

        @Override
        public void onMessage(String message) {
            messages.add(message);
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            closeCode.complete(code);
        }

        @Override
        public void onError(Exception ex) {
        }
    }
}
