package be.bendem.irc.ircsockets.ws.protocol;

import be.bendem.irc.ircsockets.Application;
import com.google.gson.JsonObject;
import org.kitteh.irc.client.library.util.Sanity;

import java.util.Optional;

public abstract class Message {

    public enum Type {
        CHANNEL_LIST,
        EVENT,
        UNKNOWN
    }

    private final Type type;
    private final boolean error;

    protected Message(Type type) {
        this(type, false);
    }

    protected Message(Type type, boolean error) {
        this.type = type;
        this.error = error;
    }

    public final String toJson() {
        JsonObject object = createJson();
        Sanity.truthiness(!object.has("_type"), getClass().getName() + " created a json object with a reserved _type");
        Sanity.truthiness(!object.has("_status"), getClass().getName() + " created a json object with a reserved _status");

        object.addProperty("_type", type.name().toLowerCase());
        object.addProperty("_status", error ? "error" : "ok");

        return Application.GSON.toJson(object);
    }

    public Optional<String> getTarget() {
        return Optional.empty();
    }

    protected abstract JsonObject createJson();

}
