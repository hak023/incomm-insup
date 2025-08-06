package com.in.amas.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * sipsvc와의 JSON/TCP 통신을 위한 메시지 DTO
 * 
 * @author InComm
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SipsvcMessage {
    
    /**
     * 메시지 타입 (auth, heartbeat, execute)
     */
    @JsonProperty("type")
    private String type;
    
    /**
     * 세션 ID
     */
    @JsonProperty("session_id")
    private String sessionId;
    
    /**
     * 클라이언트 IP
     */
    @JsonProperty("client_ip")
    private String clientIp;
    
    /**
     * MAC 주소
     */
    @JsonProperty("mac_address")
    private String macAddress;
    
    /**
     * 인증키
     */
    @JsonProperty("auth_key")
    private String authKey;
    
    /**
     * 요청 ID (execute 타입에서 사용)
     */
    @JsonProperty("request_id")
    private String requestId;
    
    /**
     * 전화번호 (execute 타입에서 사용)
     */
    @JsonProperty("phone_number")
    private String phoneNumber;
    
    /**
     * 서비스 코드
     */
    @JsonProperty("service_code")
    private String serviceCode;
    
    /**
     * 추가 데이터
     */
    @JsonProperty("data")
    private Object data;
    
    /**
     * 타임스탬프
     */
    @JsonProperty("timestamp")
    private long timestamp;
    
    /**
     * 결과 코드
     */
    @JsonProperty("result_code")
    private String resultCode;
    
    /**
     * 결과 메시지
     */
    @JsonProperty("result_message")
    private String resultMessage;
    
    /**
     * 메시지 타입 상수
     */
    public static class Type {
        public static final String AUTH = "auth";
        public static final String HEARTBEAT = "heartbeat";
        public static final String EXECUTE = "execute";
        public static final String RESPONSE = "response";
    }
    
    /**
     * 결과 코드 상수
     */
    public static class ResultCode {
        public static final String SUCCESS = "0000";
        public static final String AUTH_FAILED = "1001";
        public static final String INVALID_REQUEST = "1002";
        public static final String TIMEOUT = "1003";
        public static final String INTERNAL_ERROR = "9999";
    }
}