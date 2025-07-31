package com.in.amas.ingwclient.model.server;

import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.json.JSONObject;

@Getter
@SuperBuilder
public class ExecuteRequestDTO extends BaseRequest {
    private Integer seq;          // optional
    private String service;
    private String token;
    private JSONObject reqBody;

    public ExecuteRequestDTO(JSONObject obj) {
        super(obj.getString("cmd"), obj.getString("reqNo"));
        this.seq = obj.has("seq") ? obj.getInt("seq") : null;
        this.service = obj.getString("service");
        this.token = obj.getString("token");
        this.reqBody = obj.getJSONObject("reqBody");
    }
}
