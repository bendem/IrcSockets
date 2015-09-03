package be.bendem.irc.ircsockets.ws.protocol;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.kitteh.irc.client.library.Client;
import org.kitteh.irc.client.library.element.Channel;

import java.util.stream.Stream;

public class ChannelListMessage extends Message {

    private final JsonArray channels;

    public ChannelListMessage(Client client) {
        this(client.getChannels().stream().map(Channel::getName));
    }

    public ChannelListMessage(Stream<String> channels) {
        super(Type.CHANNEL_LIST);

        this.channels = channels
            .map(JsonPrimitive::new)
            .collect(JsonArray::new, JsonArray::add, JsonArray::addAll);
    }

    @Override
    protected JsonObject createJson() {
        JsonObject obj = new JsonObject();
        obj.add("channels", channels);
        return obj;
    }

}
