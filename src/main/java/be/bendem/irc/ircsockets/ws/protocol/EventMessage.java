package be.bendem.irc.ircsockets.ws.protocol;

import com.google.gson.JsonObject;
import org.kitteh.irc.client.library.element.Channel;
import org.kitteh.irc.client.library.element.User;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public class EventMessage extends Message {

    public final ZonedDateTime time;
    public final String channel;
    public final String prefix;
    public final String msg;

    public EventMessage(Instant time, Channel channel, User user, String msg, Object... params) {
        this(time, channel, user.getNick(), msg, params);
    }

    public EventMessage(Instant time, Channel channel, String prefix, String msg, Object... params) {
        this(time, channel.getName(), prefix, msg, params);
    }

    public EventMessage(Instant time, String channel, String prefix, String msg, Object... params) {
        super(Type.EVENT);
        this.time = time.atZone(ZoneOffset.systemDefault());
        this.channel = channel;
        this.prefix = prefix;
        this.msg = String.format(msg, params);
    }

    @Override
    public Optional<String> getTarget() {
        return Optional.of(channel);
    }

    @Override
    protected JsonObject createJson() {
        JsonObject obj = new JsonObject();

        obj.addProperty("time", time.format(DateTimeFormatter.ISO_LOCAL_TIME));
        obj.addProperty("channel", channel);
        obj.addProperty("prefix", prefix);
        obj.addProperty("message", msg);

        return obj;
    }

}
