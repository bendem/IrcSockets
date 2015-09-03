package be.bendem.irc.ircsockets.ws;

import be.bendem.irc.ircsockets.Application;
import be.bendem.irc.ircsockets.ws.protocol.ChannelListMessage;
import be.bendem.irc.ircsockets.ws.protocol.ErrorMessage;
import be.bendem.irc.ircsockets.ws.protocol.Message;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.DefaultSSLWebSocketServerFactory;
import org.java_websocket.server.WebSocketServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

public class Server extends WebSocketServer {

    private final Application app;
    private final Map<String, Set<WebSocket>> channelWebSocketMap;
    private final Thread thread;
    private volatile boolean running = true;

    public Server(Application app, BlockingQueue<Message> messageQueue, int port, boolean wsSsl) {
        super(new InetSocketAddress(port), 2, Collections.emptyList(), new CopyOnWriteArraySet<>());
        this.app = app;
        this.channelWebSocketMap = new ConcurrentHashMap<>();

        if(wsSsl) {
            try {
                setWebSocketFactory(new DefaultSSLWebSocketServerFactory(setupSsl()));
            } catch(GeneralSecurityException | IOException e) {
                app.getClient().shutdown();
                throw new RuntimeException(e);
            }
        }

        start();

        thread = new Thread(() -> {
            Message msg;
            Set<WebSocket> webSockets;

            while(running) {
                // TODO Collect and send all at once?
                try {
                    msg = messageQueue.take();
                } catch(InterruptedException e) {
                    break;
                }
                String json = msg.toJson();

                if(msg.getTarget().isPresent()) {
                    // Send to target channel
                    webSockets = channelWebSocketMap.getOrDefault(msg.getTarget().get(), Collections.emptySet());
                    if(webSockets.isEmpty()) {
                        continue;
                    }

                    webSockets.forEach(conn -> conn.send(json));
                } else {
                    // Send everywhere
                    connections().forEach(conn -> conn.send(json));
                }
            }
        });
        thread.start();
    }

    private SSLContext setupSsl() throws GeneralSecurityException, IOException {
        // load up the key store
        String storeType = "JKS";
        String keystore = "server.keystore";
        char[] storePassword = new char[] {'b', 'l', 'e', 'h', 'b', 'l', 'e', 'h'};
        char[] keyPassword = new char[] {'b', 'l', 'e', 'h', 'b', 'l', 'e', 'h'};

        KeyStore ks = KeyStore.getInstance(storeType);
        ks.load(Files.newInputStream(Paths.get("..", keystore)), storePassword);

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, keyPassword);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(ks);

        SSLContext sslContext;
        sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        return sslContext;
    }

    public void kill() {
        running = false;

        try {
            System.out.println("[DEBUG] Joining sending thread");
            thread.interrupt();
            thread.join();
        } catch(InterruptedException e) {
            e.printStackTrace();
        }

        try {
            System.out.println("[DEBUG] Stopping server");
            // stop() needs a positive number (non zero) because of a bug in the lib,
            // see https://github.com/TooTallNate/Java-WebSocket/issues/259
            // Currently using https://github.com/TooTallNate/Java-WebSocket/pull/329
            stop();
        } catch(InterruptedException | IOException e) {
            e.printStackTrace();
        }
        System.out.println("[DEBUG] Stopped");
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        System.out.printf(
            "[DEBUG] New connection from %s%n",
            conn.getRemoteSocketAddress().getHostString()
        );

        conn.send(new ChannelListMessage(app.getClient()).toJson());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        System.out.printf(
            "[DEBUG] Closing connection from %s (%d: %s)%n",
            conn.getRemoteSocketAddress().getHostString(),
            code, reason
        );

        channelWebSocketMap.forEach((k, set) -> set.remove(conn));
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        JsonObject obj;
        try {
            obj = Application.GSON.fromJson(message, JsonObject.class);
        } catch(JsonSyntaxException e) {
            conn.send(new ErrorMessage("Invalid json string").toJson());
            return;
        }

        switch(obj.get("_type").getAsString()) {
            case "listen_request":
                channelWebSocketMap.forEach((k, set) -> set.remove(conn));
                for(JsonElement channel : obj.get("channels").getAsJsonArray()) {
                    channelWebSocketMap
                        .computeIfAbsent(channel.getAsString(), k -> new CopyOnWriteArraySet<>())
                            .add(conn);
                }
                break;
            default:
                System.err.println("[WARN] Unhandled _type " + obj.get("_type").getAsString());
                break;
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("[ERROR] Connection error");
        ex.printStackTrace();

        if(conn == null) {
            return;
        }
        channelWebSocketMap.forEach((k, set) -> set.remove(conn));

        if(!conn.isClosing() && !conn.isClosed()) {
            conn.close();
        }
    }

    @Override
    protected boolean addConnection(WebSocket ws) {
        return running && connections().add(ws);
    }

    @Override
    protected boolean removeConnection(WebSocket ws) {
        return running && connections().remove(ws);
    }

}
