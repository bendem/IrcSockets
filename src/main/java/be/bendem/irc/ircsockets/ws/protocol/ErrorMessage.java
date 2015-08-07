package be.bendem.irc.ircsockets.ws.protocol;

import com.google.gson.JsonObject;

public class ErrorMessage extends Message {

    private final String msg;

    public ErrorMessage(String msg) {
        super(Type.UNKNOWN, true);
        this.msg = msg;
    }

    @Override
    protected JsonObject createJson() {
        JsonObject obj = new JsonObject();

        obj.addProperty("errorMsg", msg);

        return obj;
    }

}
