package be.bendem.irc.ircsockets;

import be.bendem.irc.ircsockets.ws.protocol.ChannelListMessage;
import be.bendem.irc.ircsockets.ws.protocol.EventMessage;
import org.java_websocket.WebSocket;
import org.kitteh.irc.client.library.element.Actor;
import org.kitteh.irc.client.library.element.Channel;
import org.kitteh.irc.client.library.element.MessageReceiver;
import org.kitteh.irc.client.library.element.User;
import org.kitteh.irc.client.library.event.channel.ChannelJoinEvent;
import org.kitteh.irc.client.library.event.channel.ChannelMessageEvent;
import org.kitteh.irc.client.library.event.channel.ChannelModeEvent;
import org.kitteh.irc.client.library.event.channel.ChannelPartEvent;
import org.kitteh.irc.client.library.event.channel.ChannelTopicEvent;
import org.kitteh.irc.client.library.event.client.RequestedChannelJoinCompleteEvent;
import org.kitteh.irc.client.library.event.client.RequestedChannelLeaveEvent;
import org.kitteh.irc.client.library.event.helper.MessageEvent;
import org.kitteh.irc.client.library.event.user.PrivateMessageEvent;
import org.kitteh.irc.client.library.event.user.UserQuitEvent;
import org.kitteh.irc.lib.net.engio.mbassy.listener.Handler;

import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class EventManager {

    private final Application app;

    public EventManager(Application app) {
        this.app = app;
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

        if(!"bendem".equals(target.getAccount().orElse(""))) {
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
                String ch = null;
                if(channel.isPresent()) {
                    ch = channel.get().getName();
                }
                if(args.length > 1) {
                    ch = args[1];
                }
                if(ch == null) {
                    target.sendMessage("no channel provided");
                    break;
                }
                app.getClient().removeChannel(ch);
                break;

            case "!close":
                app.getServer().connections().forEach(WebSocket::close);
                break;

            case "!list":
                String channels = app.getClient().getChannels().stream()
                    .map(Channel::getName)
                    .collect(Collectors.joining(", "));

                channel.map(Function.<MessageReceiver>identity()).orElse(target)
                    .sendMessage("I'm in " + channels);
                break;

            default:
                target.sendMessage("invalid command");
                break;
        }
    }

    @Handler
    public void onClientJoin(RequestedChannelJoinCompleteEvent e) {
        System.out.println("Joined " + e.getChannel().getName());
        app.addMessage(new ChannelListMessage(e.getClient()));
    }

    @Handler
    public void onClientPart(RequestedChannelLeaveEvent e) {
        System.out.println("Parted " + e.getChannel().getName());

        app.addMessage(new ChannelListMessage(e.getClient()));
    }

    @Handler
    public void onChannelMessage(ChannelMessageEvent e) {
        app.addMessage(new EventMessage(
            e.getChannel(),
            e.getActor(),
            e.getMessage()
        ));
    }

    @Handler
    public void onChannelJoin(ChannelJoinEvent e) {
        app.addMessage(new EventMessage(
            e.getChannel(),
            "-->",
            "%s has joined",
            e.getActor().getNick()
        ));
    }

    @Handler
    public void onChannelPart(ChannelPartEvent e) {
        app.addMessage(new EventMessage(
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
            e.getChannel(),
            "---",
            "%s set topic to '%s'",
            e.getTopic().getSetter().map(Actor::getName).orElse("??"),
            e.getTopic().getValue().orElseGet(() -> "<none>")
        ));
    }

}
