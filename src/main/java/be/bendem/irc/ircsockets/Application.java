package be.bendem.irc.ircsockets;

import be.bendem.irc.ircsockets.ws.Server;
import be.bendem.irc.ircsockets.ws.protocol.Message;
import com.google.gson.Gson;
import org.java_websocket.WebSocketImpl;
import org.kitteh.irc.client.library.Client;
import org.kitteh.irc.client.library.ClientBuilder;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Application {

    public static final Gson GSON = new Gson();

    private final Client client;
    private final Server server;
    private final BlockingQueue<Message> messageQueue;

    public static void main(String[] args) {
        int wsPort = 8043;
        int ircPort = 6667;
        boolean debug = false;
        boolean ircSsl = true;
        boolean wsSsl = true;
        String startupChannel = null;
        String host = null;
        String username = null;
        String password = null;
        String nick = null;
        Set<String> userAccounts = new HashSet<>();

        for(int i = 0; i < args.length; ++i) {
            switch(args[i]) {
                case "--ws-port":
                    checkIndex(i, args.length);
                    wsPort = Integer.parseInt(args[++i]);
                    break;
                case "--irc-port":
                    checkIndex(i, args.length);
                    ircPort = Integer.parseInt(args[++i]);
                    break;
                case "--host":
                    checkIndex(i, args.length);
                    host = args[++i];
                    break;
                case "--username":
                    checkIndex(i, args.length);
                    username = args[++i];
                    break;
                case "--password":
                    checkIndex(i, args.length);
                    password = args[++i];
                    break;
                case "--nick":
                    checkIndex(i, args.length);
                    nick = args[++i];
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
                    checkIndex(i, args.length);
                    startupChannel = args[++i];
                    break;
                case "--user-account":
                case "-u":
                    checkIndex(i, args.length);
                    userAccounts.add(args[++i]);
                    break;
                default:
                    System.err.println("Ignored option " + args[i]);
            }
        }

        new Application(wsPort, startupChannel, debug, ircSsl, wsSsl, ircPort, host, username, password, nick, userAccounts);
    }

    private static void checkIndex(int i, int length) {
        if(i + 1 == length) {
            throw new RuntimeException("Missing parameter value");
        }
    }

    public Application(int wsPort, String startupChannel, boolean debug, boolean ircSsl, boolean wsSsl, int ircPort,
                       String host, String username, String password, String nick, Set<String> userAccounts) {
        // TODO Nullcheck stuff
        ClientBuilder builder = Client.builder()
            .server(host)
            .server(ircPort)
            .listenException(Throwable::printStackTrace);

        if(nick != null) {
            builder
                .nick(nick)
                .realName(nick)
                .name(nick);
        }

        if(username != null) {
            builder.user(username);
        }

        if(password != null) {
            builder.serverPassword(password);
        }

        if(ircSsl) {
            builder.secure(true);
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

        client.getEventManager().registerEventListener(new EventManager(this, userAccounts));

        messageQueue = new LinkedBlockingQueue<>();
        server = new Server(this, messageQueue, wsPort, wsSsl);
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
