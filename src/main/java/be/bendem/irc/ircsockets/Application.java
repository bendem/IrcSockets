package be.bendem.irc.ircsockets;

import be.bendem.irc.ircsockets.ws.Server;
import be.bendem.irc.ircsockets.ws.protocol.Message;
import com.google.gson.Gson;
import org.java_websocket.WebSocketImpl;
import org.kitteh.irc.client.library.Client;
import org.kitteh.irc.client.library.ClientBuilder;

import java.util.concurrent.ConcurrentLinkedQueue;

public class Application {

    public static final Gson GSON = new Gson();

    private final Client client;
    private final Server server;
    private final ConcurrentLinkedQueue<Message> messageQueue;

    public static void main(String[] args) {
        int port = 8043;
        boolean debug = false;
        boolean ircSsl = true;
        boolean wsSsl = true;
        String startupChannel = "#az";

        for(int i = 0; i < args.length; ++i) {
            switch(args[i]) {
                case "--port":
                case "-p":
                    if(i + 1 == args.length) {
                        throw new RuntimeException("Dumbass");
                    }
                    port = Integer.parseInt(args[++i]);
                    break;
                case "--debug":
                case "-d":
                    debug = true;
                    break;
                case "--no-ws-ssl":
                    wsSsl = false;
                    break;
                case "--no-irc-ssl":
                    ircSsl = false;
                    break;
                case "--channel":
                case "-c":
                    startupChannel = args[++i];
                    break;
                default:
                    System.err.println("Ignored option " + args[i]);
            }
        }

        new Application(port, startupChannel, debug, ircSsl, wsSsl);
    }

    public Application(int port, String startupChannel, boolean debug, boolean ircSsl, boolean wsSsl) {
        ClientBuilder builder = Client.builder()
            .server("irc.esper.net")
            .nick("notBendem")
            .realName("KorobiConcurrent")
            .name("KorobiConcurrent")
            .user("KorobiConcurrent")
            .listenException(Throwable::printStackTrace);

        if(ircSsl) {
            builder
                .server(6697)
                .secure(true);
        } else {
            builder
                .server(6667);
        }

        if(debug) {
            WebSocketImpl.DEBUG = true;
            builder
                .listenInput(i -> System.out.println("> " + i))
                .listenOutput(i -> System.out.println("< " + i));
        }

        client = builder.build();

        if(startupChannel != null) {
            client.addChannel(startupChannel);
        }
        client.getEventManager().registerEventListener(new EventManager(this));

        messageQueue = new ConcurrentLinkedQueue<>();
        server = new Server(this, messageQueue, port, wsSsl);
    }

    public Client getClient() {
        return client;
    }

    public Server getServer() {
        return server;
    }

    public void addMessage(Message message) {
        messageQueue.offer(message);
    }

    public void kill() {
        server.kill();
        client.shutdown();
    }

}
