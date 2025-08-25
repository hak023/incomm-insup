package com.in.amas.insupclient.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WorkerThread에서 처리하는 메시지를 위한 DTO
 * 
 * @author InComm
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkerMessage {
    
    /**
     * 메시지 타입 (SIPSVC_REQUEST, INSUPC_RESPONSE)
     */
    private MessageType messageType;
    
    /**
     * 연결 ID (어느 클라이언트에서 온 요청인지 식별)
     */
    private String connectionId;
    
    /**
     * 요청 ID (요청-응답 매칭을 위한 ID)
     */
    private String requestId;
    
    /**
     * sipsvc 메시지 (messageType이 SIPSVC_REQUEST인 경우)
     */
    private SipsvcMessage sipsvcMessage;
    
    /**
     * INSUPC 메시지 (messageType이 INSUPC_RESPONSE인 경우)
     */
    private InsupcMessage insupcMessage;
    
    /**
     * 메시지 수신 시간
     */
    private long receivedTime;
    
    /**
     * 처리 시작 시간
     */
    private long processingStartTime;
    
    /**
     * 재시도 횟수
     */
    private int retryCount;
    
    /**
     * 메시지 타입 열거형
     */
    public enum MessageType {
        /**
         * sipsvc로부터 받은 요청 메시지
         */
        SIPSVC_REQUEST,
        
        /**
         * INSUPC로부터 받은 응답 메시지
         */
        INSUPC_RESPONSE
    }
    
    /**
     * sipsvc 요청 메시지 생성
     */
    public static WorkerMessage createSipsvcRequest(String connectionId, String requestId, SipsvcMessage sipsvcMessage) {
        return WorkerMessage.builder()
                .messageType(MessageType.SIPSVC_REQUEST)
                .connectionId(connectionId)
                .requestId(requestId)
                .sipsvcMessage(sipsvcMessage)
                .receivedTime(System.currentTimeMillis())
                .retryCount(0)
                .build();
    }
    
    /**
     * INSUPC 응답 메시지 생성
     */
    public static WorkerMessage createInsupcResponse(String requestId, InsupcMessage insupcMessage) {
        return WorkerMessage.builder()
                .messageType(MessageType.INSUPC_RESPONSE)
                .requestId(requestId)
                .insupcMessage(insupcMessage)
                .receivedTime(System.currentTimeMillis())
                .retryCount(0)
                .build();
    }
}
