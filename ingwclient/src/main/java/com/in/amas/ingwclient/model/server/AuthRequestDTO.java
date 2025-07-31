package com.in.amas.ingwclient.model.server;

import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.json.JSONObject;

@Getter
@SuperBuilder
public class AuthRequestDTO extends BaseRequest {
    private String ipAddr;
    private String macAddr;     // Optional
    private String accessKey;

    public AuthRequestDTO(JSONObject obj) {
        super(obj.getString("cmd"), obj.getString("reqNo"));
        this.ipAddr = obj.getString("ipAddr");
        this.macAddr = obj.optString("macAddr", "");
        this.accessKey = obj.getString("accessKey");
    }
}
