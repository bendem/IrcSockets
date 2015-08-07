package be.bendem.irc.ircsockets.ws.protocol;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.kitteh.irc.client.library.Client;
import org.kitteh.irc.client.library.element.Channel;

public class ChannelListMessage extends Message {

    private final JsonArray channels;

    public ChannelListMessage(Client client) {
        super(Type.CHANNEL_LIST);

        channels = client.getChannels().stream()
            .map(Channel::getName)
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
