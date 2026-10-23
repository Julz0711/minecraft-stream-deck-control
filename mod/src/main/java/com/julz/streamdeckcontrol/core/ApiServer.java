package com.julz.streamdeckcontrol.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft;
import org.java_websocket.exceptions.InvalidDataException;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ServerHandshakeBuilder;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The local WebSocket API. Binds to the loopback address only, rejects browser pages unless their origin is
 * allow-listed, and only accepts actions from clients that sent the pairing token in their hello.
 */
public class ApiServer {
    /** Close code sent after a hello without the right token. */
    public static final int CLOSE_PAIRING_REQUIRED = 4001;
    public static final int CLOSE_UNSUPPORTED_PROTOCOL = 4002;
    public static final int CLOSE_HELLO_TIMEOUT = 4003;

    private static final Logger LOGGER = LoggerFactory.getLogger("stream-deck-control");
    private static final long HELLO_TIMEOUT_MS = 10_000;

    public record Settings(
            Supplier<String> token,
            Supplier<List<String>> allowedOrigins,
            String modVersion,
            String minecraftVersion) {
    }

    private final Settings settings;
    private final ActionHandler actions;
    private final Consumer<String> onPairingRequested;
    private final StateTracker state = new StateTracker();
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Stream Deck Control timer");
        t.setDaemon(true);
        return t;
    });

    private Server server;

    /**
     * @param onPairingRequested called with the client's name when a client connects without the right token,
     *                           on a server thread
     */
    public ApiServer(Settings settings, ActionHandler actions, Consumer<String> onPairingRequested) {
        this.settings = settings;
        this.actions = actions;
        this.onPairingRequested = onPairingRequested;
    }

    /**
     * Binds to the first free port from {@code firstPort} on, trying {@code attempts} ports. A second game
     * instance thereby gets the next port.
     *
     * @return the bound port, or -1 if none was free
     */
    public synchronized int start(int firstPort, int attempts) {
        stop();
        for (int port = firstPort; port < firstPort + attempts && port <= 65535; port++) {
            Server candidate = new Server(port);
            if (candidate.startAndWait()) {
                server = candidate;
                LOGGER.info("Stream Deck Control API listening on 127.0.0.1:{}", candidate.getPort());
                return candidate.getPort();
            }
        }
        LOGGER.error("Stream Deck Control could not bind any port from {} to {}", firstPort, firstPort + attempts - 1);
        return -1;
    }

    public synchronized void stop() {
        if (server != null) {
            try {
                server.stop(1000, "Minecraft is closing");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            server = null;
        }
    }

    public synchronized int port() {
        return server == null ? -1 : server.getPort();
    }

    /** Number of paired clients connected right now. */
    public synchronized int clientCount() {
        if (server == null) {
            return 0;
        }
        return (int) server.getConnections().stream().filter(ApiServer::isPaired).count();
    }

    /** Closes all connections, e.g. after the pairing code changed. Clients then have to pair again. */
    public synchronized void disconnectAll() {
        if (server != null) {
            for (WebSocket conn : server.getConnections()) {
                conn.close(CLOSE_PAIRING_REQUIRED, "Pairing code changed");
            }
        }
    }

    /** Sends the keys that changed since the last snapshot to every paired client. */
    public void publishState(JsonObject snapshot) {
        JsonObject changes = state.update(snapshot);
        Server current;
        synchronized (this) {
            current = server;
        }
        if (changes.isEmpty() || current == null) {
            return;
        }
        String message = Protocol.state(changes).toString();
        for (WebSocket conn : current.getConnections()) {
            if (isPaired(conn) && conn.isOpen()) {
                conn.send(message);
            }
        }
    }

    private static boolean isPaired(WebSocket conn) {
        return Boolean.TRUE.equals(conn.getAttachment());
    }

    private void handle(WebSocket conn, String text) {
        JsonObject msg;
        try {
            JsonElement parsed = JsonParser.parseString(text);
            if (!parsed.isJsonObject()) {
                throw new JsonParseException("not an object");
            }
            msg = parsed.getAsJsonObject();
        } catch (JsonParseException e) {
            send(conn, Protocol.error(Protocol.BAD_REQUEST, "Messages must be JSON objects"));
            return;
        }
        String type = string(msg, "type");
        if ("hello".equals(type)) {
            hello(conn, msg);
        } else if ("action".equals(type)) {
            action(conn, msg);
        } else {
            send(conn, Protocol.error(Protocol.BAD_REQUEST, "Unknown message type: " + type));
        }
    }

    private void hello(WebSocket conn, JsonObject msg) {
        int protocol = msg.has("protocol") && msg.get("protocol").isJsonPrimitive() ? msg.get("protocol").getAsInt() : -1;
        if (protocol != Protocol.VERSION) {
            send(conn, Protocol.error(Protocol.UNSUPPORTED_PROTOCOL, "This mod speaks protocol " + Protocol.VERSION));
            conn.close(CLOSE_UNSUPPORTED_PROTOCOL, "Unsupported protocol");
            return;
        }
        if (!PairingToken.matches(settings.token().get(), string(msg, "token"))) {
            send(conn, Protocol.error(Protocol.PAIRING_REQUIRED, "Enter the pairing code shown in Minecraft"));
            conn.close(CLOSE_PAIRING_REQUIRED, "Pairing required");
            String client = string(msg, "client");
            onPairingRequested.accept(client == null || client.isBlank() ? "A program" : client);
            return;
        }
        conn.setAttachment(Boolean.TRUE);
        send(conn, Protocol.welcome(settings.modVersion(), settings.minecraftVersion(), actions.actionNames(), state.snapshot()));
    }

    private void action(WebSocket conn, JsonObject msg) {
        if (!isPaired(conn)) {
            send(conn, Protocol.error(Protocol.NOT_PAIRED, "Send a hello with the pairing code first"));
            return;
        }
        String id = msg.has("id") && msg.get("id").isJsonPrimitive() ? msg.get("id").getAsString() : null;
        String name = string(msg, "name");
        if (id == null || name == null) {
            send(conn, Protocol.error(Protocol.BAD_REQUEST, "An action needs an id and a name"));
            return;
        }
        if (!actions.actionNames().contains(name)) {
            send(conn, Protocol.result(id, ActionResult.fail(Protocol.UNKNOWN_ACTION, "Unknown action: " + name)));
            return;
        }
        JsonObject args = msg.has("args") && msg.get("args").isJsonObject() ? msg.getAsJsonObject("args") : new JsonObject();
        actions.run(name, args).whenComplete((result, error) -> {
            if (error != null) {
                LOGGER.error("Action {} failed", name, error);
                result = ActionResult.fail(Protocol.INTERNAL, "The action failed, see the Minecraft log");
            }
            send(conn, Protocol.result(id, result));
        });
    }

    private static void send(WebSocket conn, JsonObject msg) {
        if (conn.isOpen()) {
            conn.send(msg.toString());
        }
    }

    private static String string(JsonObject msg, String key) {
        JsonElement value = msg.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    /** Browsers always send an Origin header; local programs such as the Stream Deck plugin don't. */
    static boolean originAllowed(String origin, List<String> allowed) {
        return origin == null || origin.isEmpty() || allowed.contains(origin);
    }

    private class Server extends WebSocketServer {
        private final CountDownLatch started = new CountDownLatch(1);
        private volatile boolean bound;

        Server(int port) {
            super(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
            setReuseAddr(false);
            setConnectionLostTimeout(30);
            setDaemon(true);
        }

        boolean startAndWait() {
            start();
            try {
                started.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (!bound) {
                try {
                    stop(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return bound;
        }

        @Override
        public ServerHandshakeBuilder onWebsocketHandshakeReceivedAsServer(WebSocket conn, Draft draft, ClientHandshake request)
                throws InvalidDataException {
            String origin = request.getFieldValue("Origin");
            if (!originAllowed(origin, settings.allowedOrigins().get())) {
                LOGGER.warn("Stream Deck Control rejected a connection from web page {}", origin);
                throw new InvalidDataException(CloseFrame.POLICY_VALIDATION, "Origin not allowed");
            }
            return super.onWebsocketHandshakeReceivedAsServer(conn, draft, request);
        }

        @Override
        public void onStart() {
            bound = true;
            started.countDown();
        }

        @Override
        public void onOpen(WebSocket conn, ClientHandshake handshake) {
            timer.schedule(() -> {
                if (conn.isOpen() && !isPaired(conn)) {
                    conn.close(CLOSE_HELLO_TIMEOUT, "No hello received");
                }
            }, HELLO_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }

        @Override
        public void onMessage(WebSocket conn, String message) {
            handle(conn, message);
        }

        @Override
        public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        }

        @Override
        public void onError(WebSocket conn, Exception ex) {
            if (conn == null) {
                // Binding failed, usually because another program (or game instance) has the port
                LOGGER.debug("Stream Deck Control server error", ex);
                started.countDown();
            } else {
                LOGGER.warn("Stream Deck Control connection error", ex);
            }
        }
    }
}
