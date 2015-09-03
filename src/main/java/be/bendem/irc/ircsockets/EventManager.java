package be.bendem.irc.ircsockets;

import be.bendem.irc.ircsockets.ws.protocol.ChannelListMessage;
import be.bendem.irc.ircsockets.ws.protocol.EventMessage;
import org.java_websocket.WebSocket;
import org.kitteh.irc.client.library.element.Actor;
import org.kitteh.irc.client.library.element.Channel;
import org.kitteh.irc.client.library.element.MessageReceiver;
import org.kitteh.irc.client.library.element.MessageTag;
import org.kitteh.irc.client.library.element.User;
import org.kitteh.irc.client.library.event.channel.ChannelCTCPEvent;
import org.kitteh.irc.client.library.event.channel.ChannelJoinEvent;
import org.kitteh.irc.client.library.event.channel.ChannelMessageEvent;
import org.kitteh.irc.client.library.event.channel.ChannelModeEvent;
import org.kitteh.irc.client.library.event.channel.ChannelPartEvent;
import org.kitteh.irc.client.library.event.channel.ChannelTopicEvent;
import org.kitteh.irc.client.library.event.client.RequestedChannelJoinCompleteEvent;
import org.kitteh.irc.client.library.event.helper.MessageEvent;
import org.kitteh.irc.client.library.event.helper.ServerMessageEvent;
import org.kitteh.irc.client.library.event.user.PrivateMessageEvent;
import org.kitteh.irc.client.library.event.user.UserQuitEvent;
import org.kitteh.irc.lib.net.engio.mbassy.listener.Handler;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class EventManager {

    private final Application app;
    private final Set<String> userAccounts;

    public EventManager(Application app, Set<String> userAccounts) {
        this.app = app;
        this.userAccounts = userAccounts;
    }

    @Handler
    public void onPrivateCommand(PrivateMessageEvent e) {
        handleCommand(e, e.getActor(), Optional.empty());
    }

    @Handler
    public void onCommand(ChannelMessageEvent e) {
        handleCommand(e, e.getActor(), Optional.of(e.getChannel()));
    }

    private void handleCommand(MessageEvent e, User target, Optional<Channel> channel) {
        String message = e.getMessage();

        if(!message.startsWith("!")) {
            return;
        }

        if(!userAccounts.contains(target.getAccount().orElse(""))) {
            return;
        }

        String[] args = message.split("\\s+");
        switch(args[0]) {
            case "!quit":
                app.kill();
                break;

            case "!join":
                if(args.length < 2) {
                    target.sendMessage("no channel provided, asshole");
                    break;
                }
                app.getClient().addChannel(args[1]);
                break;

            case "!leave":
            case "!part":
                execForChannel(channel, args, target, app.getClient()::removeChannel);
                break;

            case "!close":
                app.getServer().connections().forEach(WebSocket::close);
                break;

            case "!list":
                String channels = app.getClient().getChannels().stream()
                    .map(Channel::getName)
                    .sorted()
                    .collect(Collectors.joining(", "));

                channel.map(Function.<MessageReceiver>identity()).orElse(target)
                    .sendMessage("I'm in " + channels);
                break;

            case "!spam":
                execForChannel(channel, args, target, ch -> {
                    EventMessage yolo = new EventMessage(
                        Instant.now(),
                        ch,
                        "*spam",
                        "yolo"
                    );
                    for(int i = 0; i < 20; ++i) {
                        app.addMessage(yolo);
                    }
                });
                break;

            default:
                target.sendMessage("invalid command");
                break;
        }
    }

    private void execForChannel(Optional<Channel> channel, String[] args, User target, Consumer<String> action) {
        String ch = null;
        if(channel.isPresent()) {
            ch = channel.get().getName();
        }
        if(args.length > 1) {
            ch = args[1];
        }

        if(ch == null) {
            target.sendMessage("no channel provided");
        } else {
            action.accept(ch);
        }
    }

    @Handler
    public void onClientJoin(RequestedChannelJoinCompleteEvent e) {
        System.out.println("Joined " + e.getChannel().getName());
        app.addMessage(new ChannelListMessage(e.getClient()));
    }

    @Handler
    public void onClientPart(ChannelPartEvent e) {
        if(!e.getActor().getNick().equals(e.getClient().getNick())) {
            return;
        }
        System.out.println("Parted " + e.getChannel().getName());

        Stream<String> channels = e.getClient().getChannels().stream()
            .filter(c -> c != e.getChannel())
            .map(Channel::getName);

        app.addMessage(new ChannelListMessage(channels));
    }

    @Handler(priority = 1) // Receive the message before handling commands
    public void onChannelMessage(ChannelMessageEvent e) {
        app.addMessage(new EventMessage(
            getEventTime(e),
            e.getChannel(),
            e.getActor(),
            e.getMessage()
        ));
    }

    @Handler
    public void onChannelAction(ChannelCTCPEvent e) {
        if (!e.getMessage().startsWith("ACTION ")) {
            return;
        }

        app.addMessage(new EventMessage(
            getEventTime(e),
            e.getChannel(),
            "*",
            "%s %s",
            e.getActor().getNick(),
            e.getMessage().substring("ACTION ".length())
        ));
    }

    @Handler
    public void onChannelJoin(ChannelJoinEvent e) {
        app.addMessage(new EventMessage(
            getEventTime(e),
            e.getChannel(),
            "-->",
            "%s has joined",
            e.getActor().getNick()
        ));
    }

    @Handler
    public void onChannelPart(ChannelPartEvent e) {
        app.addMessage(new EventMessage(
            getEventTime(e),
            e.getChannel(),
            "<--",
            "%s has left (%s)",
            e.getActor().getNick(),
            e.getMessage()
        ));
    }

    @Handler
    public void onChannelUserQuit(UserQuitEvent e) {
        e.getActor().getChannels().forEach(channel ->
            app.addMessage(new EventMessage(
                getEventTime(e),
                channel,
                "<--",
                "%s has quit (%s)",
                e.getActor().getNick(),
                e.getMessage()
            )));
    }

    @Handler
    public void onChannelMode(ChannelModeEvent e) {
        app.addMessage(new EventMessage(
            getEventTime(e),
            e.getChannel(),
            "---",
            "%s set mode %s",
            e.getActor().getName(),
            e.getStatusList().getStatusString()
        ));
    }

    @Handler
    public void onChannelTopic(ChannelTopicEvent e) {
        if(!e.isNew()) {
            return;
        }

        app.addMessage(new EventMessage(
            getEventTime(e),
            e.getChannel(),
            "---",
            "%s set topic to '%s'",
            e.getTopic().getSetter().map(Actor::getName).orElse("??"),
            e.getTopic().getValue().orElseGet(() -> "<none>")
        ));
    }

    private Instant getEventTime(ServerMessageEvent e) {
        return e.getOriginalMessages().stream()
            .flatMap(m -> m.getTags().stream())
                .filter(tag -> tag instanceof MessageTag.Time)
                .map(tag -> (MessageTag.Time) tag)
                .findFirst()
                    .map(MessageTag.Time::getTime)
                    .orElseGet(Instant::now);
    }

}
