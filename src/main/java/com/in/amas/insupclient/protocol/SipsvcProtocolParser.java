package com.in.amas.insupclient.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.in.amas.insupclient.dto.SipsvcMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * sipsvc와의 JSON/TCP 프로토콜 파서
 * 
 * @author InComm
 * @version 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SipsvcProtocolParser {
    
    private final ObjectMapper objectMapper;
    
    /**
     * JSON 바이트 배열을 SipsvcMessage로 파싱
     * 
     * @param data JSON 바이트 배열
     * @return 파싱된 SipsvcMessage
     * @throws Exception 파싱 에러
     */
    public SipsvcMessage parseMessage(byte[] data) throws Exception {
        try {
            String jsonString = new String(data, StandardCharsets.UTF_8);
            log.debug(">>> sipsvc 메시지 수신: {}", jsonString);
            
            SipsvcMessage message = objectMapper.readValue(jsonString, SipsvcMessage.class);
            
            // 기본값 설정
            if (message.getTimestamp() == 0) {
                message.setTimestamp(System.currentTimeMillis());
            }
            
            log.info(">>> sipsvc 메시지 파싱 완료 - 타입: {}, 세션: {}, 클라이언트: {}", 
                    message.getType(), message.getSessionId(), message.getClientIp());
            
            return message;
            
        } catch (Exception e) {
            log.error(">>> sipsvc 메시지 파싱 실패: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * SipsvcMessage를 JSON 바이트 배열로 직렬화
     * 
     * @param message SipsvcMessage 객체
     * @return JSON 바이트 배열
     * @throws Exception 직렬화 에러
     */
    public byte[] serializeMessage(SipsvcMessage message) throws Exception {
        try {
            // 타임스탬프 설정
            if (message.getTimestamp() == 0) {
                message.setTimestamp(System.currentTimeMillis());
            }
            
            String jsonString = objectMapper.writeValueAsString(message);
            log.debug("<<< sipsvc 메시지 송신: {}", jsonString);
            
            byte[] data = jsonString.getBytes(StandardCharsets.UTF_8);
            
            log.info("<<< sipsvc 메시지 직렬화 완료 - 타입: {}, 세션: {}, 크기: {} bytes", 
                    message.getType(), message.getSessionId(), data.length);
            
            return data;
            
        } catch (Exception e) {
            log.error("<<< sipsvc 메시지 직렬화 실패: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 인증 응답 메시지 생성
     * 
     * @param request 인증 요청 메시지
     * @param success 인증 성공 여부
     * @param message 응답 메시지
     * @return 인증 응답 메시지
     */
    public SipsvcMessage createAuthResponse(SipsvcMessage request, boolean success, String message) {
        return SipsvcMessage.builder()
                .type(SipsvcMessage.Type.RESPONSE)
                .sessionId(request.getSessionId())
                .clientIp(request.getClientIp())
                .resultCode(success ? SipsvcMessage.ResultCode.SUCCESS : SipsvcMessage.ResultCode.AUTH_FAILED)
                .resultMessage(message)
                .timestamp(System.currentTimeMillis())
                .build();
    }
    
    /**
     * heartbeat 응답 메시지 생성
     * 
     * @param request heartbeat 요청 메시지
     * @return heartbeat 응답 메시지
     */
    public SipsvcMessage createHeartbeatResponse(SipsvcMessage request) {
        return SipsvcMessage.builder()
                .type(SipsvcMessage.Type.RESPONSE)
                .sessionId(request.getSessionId())
                .clientIp(request.getClientIp())
                .resultCode(SipsvcMessage.ResultCode.SUCCESS)
                .resultMessage("Heartbeat OK")
                .timestamp(System.currentTimeMillis())
                .build();
    }
    
    /**
     * execute 응답 메시지 생성
     * 
     * @param request execute 요청 메시지
     * @param data 응답 데이터
     * @param resultCode 결과 코드
     * @param resultMessage 결과 메시지
     * @return execute 응답 메시지
     */
    public SipsvcMessage createExecuteResponse(SipsvcMessage request, Object data, String resultCode, String resultMessage) {
        return SipsvcMessage.builder()
                .type(SipsvcMessage.Type.RESPONSE)
                .sessionId(request.getSessionId())
                .requestId(request.getRequestId())
                .clientIp(request.getClientIp())
                .phoneNumber(request.getPhoneNumber())
                .serviceCode(request.getServiceCode())
                .data(data)
                .resultCode(resultCode)
                .resultMessage(resultMessage)
                .timestamp(System.currentTimeMillis())
                .build();
    }
    
    /**
     * 메시지 유효성 검증
     * 
     * @param message 검증할 메시지
     * @return 유효성 검증 결과
     */
    public boolean validateMessage(SipsvcMessage message) {
        if (message == null) {
            log.warn("메시지가 null입니다.");
            return false;
        }
        
        if (message.getType() == null || message.getType().trim().isEmpty()) {
            log.warn("메시지 타입이 없습니다.");
            return false;
        }
        
        if (message.getSessionId() == null || message.getSessionId().trim().isEmpty()) {
            log.warn("세션 ID가 없습니다.");
            return false;
        }
        
        // execute 타입의 경우 추가 검증
        if (SipsvcMessage.Type.EXECUTE.equals(message.getType())) {
            if (message.getRequestId() == null || message.getRequestId().trim().isEmpty()) {
                log.warn("execute 메시지에 요청 ID가 없습니다.");
                return false;
            }
            if (message.getPhoneNumber() == null || message.getPhoneNumber().trim().isEmpty()) {
                log.warn("execute 메시지에 전화번호가 없습니다.");
                return false;
            }
        }
        
        return true;
    }
}
