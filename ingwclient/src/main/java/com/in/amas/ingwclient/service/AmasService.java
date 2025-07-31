package com.in.amas.ingwclient.service;

import com.in.amas.ingwclient.config.ApplicationProperties;
import com.in.amas.ingwclient.model.server.AuthRequestDTO;
import com.in.amas.ingwclient.model.server.ExecuteRequestDTO;
import com.in.amas.ingwclient.model.server.HeartbeatRequestDTO;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AmasService {
    private final Map<String, SessionInfo> sessionStore = new ConcurrentHashMap<>();

    private static final long SESSION_EXPIRATION_MS = 2 * 60 * 60 * 1000L;

    private final ApplicationProperties prop;

    public AmasService(ApplicationProperties applicationProperties) {
        this.prop = applicationProperties;
    }

    public String authenticate(AuthRequestDTO req) {
        if (!isAuthorized(req.getIpAddr(), req.getMacAddr(), req.getAccessKey())) {
            return buildAuthResponse(-1, "Access denied", req.getReqNo(), null, "Block");
        }
        String token = generateToken();
        sessionStore.put(token, new SessionInfo(token, Instant.now().toEpochMilli() + SESSION_EXPIRATION_MS, "Active"));
        return buildAuthResponse(0, "Success authenticated", req.getReqNo(), token, "Active");
    }

    public String checkSession(HeartbeatRequestDTO req) {
        SessionInfo session = sessionStore.get(req.getToken());
        if (session == null) {
            return buildCommonResponse(-1, "Token expired", req.getReqNo(), "Blocked");
        }

        session.setExpireTime(Instant.now().toEpochMilli() + SESSION_EXPIRATION_MS);
        return buildCommonResponse(0, "Success update session", req.getReqNo(), "Running");
    }

    public String executeService(ExecuteRequestDTO req) {
        SessionInfo session = sessionStore.get(req.getToken());
        if (session == null) {
            return buildCommonExecuteResponse(-1, "Token expired", req.getReqNo(), req.getSeq(), req.getService(), "Blocked", null);
        }

        session.setExpireTime(Instant.now().toEpochMilli() + SESSION_EXPIRATION_MS);

        JSONObject rspBody = null;
        if ("INSUP".equalsIgnoreCase(req.getService())) {
            rspBody = handleINSUP(req.getReqBody());
        } else {
            rspBody = new JSONObject();
            rspBody.put("result", -100);
            rspBody.put("resultDesc", "Not supported");
        }

        return buildCommonExecuteResponse(0, "Success execute", req.getReqNo(), req.getSeq(), req.getService(), "Running", rspBody);
    }

    private JSONObject handleINSUP(JSONObject reqBody) {
        String apiName = reqBody.optString("apiName");
        JSONObject rsp = new JSONObject();
        rsp.put("result", 0);
        rsp.put("apiName", apiName);
        rsp.put("outputParams", reqBody.optJSONArray("inputParams"));
        return rsp;
    }

    private boolean isAuthorized(String ip, String mac, String accessKey) {
        return "ZGV2TWFzdGVyS2V5".equals(accessKey);
    }

    private String generateToken() {
        String uuid = UUID.randomUUID().toString();
        return Base64.getEncoder().encodeToString(uuid.getBytes(StandardCharsets.UTF_8));
    }

    private String buildAuthResponse(int result, String desc, String reqNo, String token, String sessionState) {
        JSONObject obj = new JSONObject();
        obj.put("result", result);
        obj.put("resultDesc", desc);
        obj.put("reqNo", reqNo);
        obj.put("sessionState", sessionState);
        if (token != null) obj.put("token", token);
        return wrapResponse(obj);
    }

    private String buildCommonResponse(int result, String desc, String reqNo, String sessionState) {
        JSONObject obj = new JSONObject();
        obj.put("result", result);
        obj.put("resultDesc", desc);
        obj.put("reqNo", reqNo);
        obj.put("sessionState", sessionState);
        return wrapResponse(obj);
    }

    private String buildCommonExecuteResponse(int result, String desc, String reqNo, Integer seq, String service,
                                              String sessionState, JSONObject rspBody) {
        JSONObject obj = new JSONObject();
        obj.put("cmd", "execute");
        if (seq != null) obj.put("seq", seq);
        obj.put("service", service);
        obj.put("result", result);
        obj.put("resultDesc", desc);
        obj.put("reqNo", reqNo);
        obj.put("sessionState", sessionState);
        obj.put("rspBody", rspBody == null ? new JSONObject() : rspBody);
        return wrapResponse(obj);
    }

    private String wrapResponse(JSONObject json) {
        String body = json.toString();
        int len = body.getBytes(StandardCharsets.UTF_8).length;
        return String.format("(%08d)%s", len, body);
    }

    private static class SessionInfo {
        private String token;
        private long expireTime;
        private String sessionState;

        public SessionInfo(String token, long expireTime, String sessionState) {
            this.token = token;
            this.expireTime = expireTime;
            this.sessionState = sessionState;
        }

        public long getExpireTime() {
            return expireTime;
        }

        public void setExpireTime(long expireTime) {
            this.expireTime = expireTime;
        }

        public String getSessionState() {
            return sessionState;
        }
    }
}
