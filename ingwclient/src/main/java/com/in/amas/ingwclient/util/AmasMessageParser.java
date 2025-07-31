package com.in.amas.ingwclient.util;

import com.in.amas.ingwclient.model.server.AuthRequestDTO;
import com.in.amas.ingwclient.model.server.BaseRequest;
import com.in.amas.ingwclient.model.server.ExecuteRequestDTO;
import com.in.amas.ingwclient.model.server.HeartbeatRequestDTO;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

public class AmasMessageParser {

    public static String extractBody(String raw) {
        return raw;
    }

    public static BaseRequest parse(String json) {
        JSONObject obj = new JSONObject(json);
        String cmd = obj.getString("cmd");
        switch (cmd) {
            case "auth":
                return new AuthRequestDTO(obj);
            case "heartBeat":
                return new HeartbeatRequestDTO(obj);
            case "execute":
                return new ExecuteRequestDTO(obj);
            default:
                throw new IllegalArgumentException("Unknown command: " + cmd);
        }
    }

    public static String wrapResponse(String jsonBody) {
        int length = jsonBody.getBytes(StandardCharsets.UTF_8).length;
        return String.format("(%08d)%s", length, jsonBody);
    }
}
