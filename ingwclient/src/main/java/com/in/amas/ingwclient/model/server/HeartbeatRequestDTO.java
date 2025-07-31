package com.in.amas.ingwclient.model.server;

import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.json.JSONObject;

@Getter
@SuperBuilder
public class HeartbeatRequestDTO extends BaseRequest {
    private String token;

    public HeartbeatRequestDTO(JSONObject obj) {
        super(obj.getString("cmd"), obj.getString("reqNo"));
        this.token = obj.getString("token");
    }
}
