package com.in.amas.insupclient.service;

import com.in.amas.insupclient.dto.WorkerMessage;
import com.in.amas.insupclient.dto.SipsvcMessage;
import com.in.amas.insupclient.dto.InsupcMessage;
import com.in.amas.insupclient.protocol.SipsvcProtocolParser;
import com.in.amas.insupclient.protocol.InsupcProtocolParser;
import com.in.amas.insupclient.tcp.SipsvcTcpServer;
import com.in.amas.insupclient.tcp.InsupcTcpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 메시지 처리 서비스
 * WorkerThread에서 호출되어 실제 비즈니스 로직을 처리
 * 
 * @author InComm
 * @version 1.0.0
 */
@Slf4j
@Service
public class MessageProcessingService {
    
    private final SipsvcProtocolParser sipsvcProtocolParser;
    private final InsupcProtocolParser insupcProtocolParser;
    private final ApplicationContext applicationContext;
    
    // ConnectionManagementService는 순환 참조 방지를 위해 지연 로딩
    private ConnectionManagementService connectionManagementService;
    
    public MessageProcessingService(SipsvcProtocolParser sipsvcProtocolParser,
                                  InsupcProtocolParser insupcProtocolParser,
                                  ApplicationContext applicationContext) {
        this.sipsvcProtocolParser = sipsvcProtocolParser;
        this.insupcProtocolParser = insupcProtocolParser;
        this.applicationContext = applicationContext;
    }
    
    // 요청-응답 매핑을 위한 맵 (요청 ID -> 연결 ID)
    private final Map<String, String> requestConnectionMap = new ConcurrentHashMap<>();
    
    /**
     * sipsvc 요청 메시지 처리
     * 
     * @param workerMessage 워커 메시지
     */
    public void processSipsvcRequest(WorkerMessage workerMessage) {
        SipsvcMessage sipsvcMessage = workerMessage.getSipsvcMessage();
        String connectionId = workerMessage.getConnectionId();
        String requestId = workerMessage.getRequestId();
        
        log.info("sipsvc request processing started - connection ID: {}, request ID: {}, type: {}", 
                connectionId, requestId, sipsvcMessage.getType());
        
        try {
            switch (sipsvcMessage.getType()) {
                case SipsvcMessage.Type.AUTH:
                    handleAuthRequest(sipsvcMessage, connectionId);
                    break;
                    
                case SipsvcMessage.Type.HEARTBEAT:
                    handleHeartbeatRequest(sipsvcMessage, connectionId);
                    break;
                    
                case SipsvcMessage.Type.EXECUTE:
                    handleExecuteRequest(sipsvcMessage, connectionId, requestId);
                    break;
                    
                default:
                    log.warn("Unknown sipsvc message type: {}", sipsvcMessage.getType());
                    sendErrorResponse(sipsvcMessage, connectionId, "Unknown message type");
                    break;
            }
            
        } catch (Exception e) {
            log.error("Error during sipsvc request processing - connection ID: {}, request ID: {}, error: {}", 
                    connectionId, requestId, e.getMessage(), e);
            sendErrorResponse(sipsvcMessage, connectionId, "Internal server error");
        }
    }
    
    /**
     * INSUPC 응답 메시지 처리
     * 
     * @param workerMessage 워커 메시지
     */
    public void processInsupcResponse(WorkerMessage workerMessage) {
        InsupcMessage insupcMessage = workerMessage.getInsupcMessage();
        String requestId = workerMessage.getRequestId();
        
        log.info("INSUPC response processing started - request ID: {}, code: {}", 
                requestId, insupcMessage.getMsgCode());
        
        try {
            // 요청 ID로 원래 연결 찾기
            String connectionId = requestConnectionMap.remove(requestId);
            
            if (connectionId == null) {
                log.warn("Cannot find connection for INSUPC response - request ID: {}", requestId);
                return;
            }
            
            // INSUPC 응답을 sipsvc 응답으로 변환
            SipsvcMessage responseMessage = convertInsupcToSipsvc(insupcMessage, requestId);
            
            // sipsvc로 응답 전송
            getConnectionManagementService().sendToSipsvc(connectionId, responseMessage);
            
            log.info("INSUPC response forwarded to sipsvc completed - connection ID: {}, request ID: {}", 
                    connectionId, requestId);
            
        } catch (Exception e) {
            log.error("Error during INSUPC response processing - request ID: {}, error: {}", 
                    requestId, e.getMessage(), e);
        }
    }
    
    /**
     * 메시지 처리 실패 시 호출
     * 
     * @param workerMessage 실패한 메시지
     * @param exception 발생한 예외
     */
    public void handleFailedMessage(WorkerMessage workerMessage, Exception exception) {
        log.error("Message processing final failure - request ID: {}, type: {}, error: {}", 
                workerMessage.getRequestId(), 
                workerMessage.getMessageType(), 
                exception.getMessage(), exception);
        
        // 실패한 메시지에 대한 처리 (DLQ, 알림 등)
        if (workerMessage.getMessageType() == WorkerMessage.MessageType.SIPSVC_REQUEST) {
            SipsvcMessage errorResponse = sipsvcProtocolParser.createExecuteResponse(
                    workerMessage.getSipsvcMessage(),
                    null,
                    SipsvcMessage.ResultCode.INTERNAL_ERROR,
                    "Message processing failed: " + exception.getMessage()
            );
            
            getConnectionManagementService().sendToSipsvc(
                    workerMessage.getConnectionId(), 
                    errorResponse
            );
        }
        
        // 요청-응답 매핑 정리
        requestConnectionMap.remove(workerMessage.getRequestId());
    }
    
    /**
     * 인증 요청 처리
     */
    private void handleAuthRequest(SipsvcMessage request, String connectionId) {
        log.info("Authentication request processing - connection ID: {}, client IP: {}", connectionId, request.getClientIp());
        
        // 인증 검증
        boolean isAuthenticated = getConnectionManagementService().authenticateClient(
                request.getClientIp(),
                request.getMacAddress(),
                request.getAuthKey()
        );
        
        SipsvcMessage response;
        if (isAuthenticated) {
            response = sipsvcProtocolParser.createAuthResponse(request, true, "Authentication successful");
            getConnectionManagementService().updateClientAuthentication(connectionId, true);
            log.info("Client authentication successful - connection ID: {}", connectionId);
        } else {
            response = sipsvcProtocolParser.createAuthResponse(request, false, "Authentication failed");
            log.warn("Client authentication failed - connection ID: {}, IP: {}", connectionId, request.getClientIp());
        }
        
        getConnectionManagementService().sendToSipsvc(connectionId, response);
    }
    
    /**
     * heartbeat 요청 처리
     */
    private void handleHeartbeatRequest(SipsvcMessage request, String connectionId) {
        log.debug("Heartbeat request processing - connection ID: {}", connectionId);
        
        getConnectionManagementService().updateClientHeartbeat(connectionId);
        
        SipsvcMessage response = sipsvcProtocolParser.createHeartbeatResponse(request);
        getConnectionManagementService().sendToSipsvc(connectionId, response);
    }
    
    /**
     * execute 요청 처리 (INSUPC로 질의)
     */
    private void handleExecuteRequest(SipsvcMessage request, String connectionId, String requestId) {
        log.info("Execute request processing - connection ID: {}, request ID: {}, phone number: {}", 
                connectionId, requestId, request.getPhoneNumber());
        
        // 연결이 인증되었는지 확인
        if (!getConnectionManagementService().isClientAuthenticated(connectionId)) {
            log.warn("Execute request from unauthenticated client - connection ID: {}", connectionId);
            sendErrorResponse(request, connectionId, "Not authenticated");
            return;
        }
        
        // 요청-응답 매핑 저장
        requestConnectionMap.put(requestId, connectionId);
        
        try {
            // INSUPC 질의 메시지 생성 (C++ 구현과 동일)
            java.util.List<String> inputValues = java.util.List.of(request.getPhoneNumber());
            InsupcMessage queryRequest = insupcProtocolParser.createQueryRequest(
                    "mcidPstnGetInfoV2",  // API 이름
                    inputValues,          // 입력 파라미터
                    1                     // INAS ID
            );
            
            // INSUPC로 질의 전송
            getConnectionManagementService().sendToInsupc(queryRequest, requestId);
            
            log.info("INSUPC query sent successfully - request ID: {}, phone number: {}", 
                    requestId, request.getPhoneNumber());
            
        } catch (Exception e) {
            log.error("INSUPC query send failed - request ID: {}, error: {}", requestId, e.getMessage(), e);
            
            // 매핑 제거
            requestConnectionMap.remove(requestId);
            
            // 오류 응답 전송
            sendErrorResponse(request, connectionId, "Failed to query INSUPC");
        }
    }
    
    /**
     * INSUPC 응답을 sipsvc 응답으로 변환
     */
    private SipsvcMessage convertInsupcToSipsvc(InsupcMessage insupcMessage, String requestId) {
        // INSUPC 응답 데이터 파싱
        Map<String, Object> responseData = new HashMap<>();
        
        if (insupcMessage.getParameters() != null) {
            for (InsupcMessage.InsupcParameter param : insupcMessage.getParameters()) {
                switch (param.getType()) {
                    case InsupcMessage.InsupcParameter.Type.SQL_OUTPUT:
                        responseData.put("sql_output", param.getValue());
                        break;
                    case InsupcMessage.InsupcParameter.Type.SQL_RESULT:
                        responseData.put("sql_result", param.getValue());
                        break;
                    case InsupcMessage.InsupcParameter.Type.DB_OPERATION_NAME:
                        responseData.put("operation", param.getValue());
                        break;
                }
            }
        }
        
        // 결과 코드 결정
        String resultCode = (insupcMessage.getResult() == 1) ? 
                SipsvcMessage.ResultCode.SUCCESS : 
                SipsvcMessage.ResultCode.INTERNAL_ERROR;
        
        String resultMessage = (insupcMessage.getResult() == 1) ? 
                "Query successful" : 
                "Query failed";
        
        return SipsvcMessage.builder()
                .type(SipsvcMessage.Type.RESPONSE)
                .requestId(requestId)
                .data(responseData)
                .resultCode(resultCode)
                .resultMessage(resultMessage)
                .timestamp(System.currentTimeMillis())
                .build();
    }
    
    /**
     * 오류 응답 전송
     */
    private void sendErrorResponse(SipsvcMessage request, String connectionId, String errorMessage) {
        SipsvcMessage errorResponse = sipsvcProtocolParser.createExecuteResponse(
                request,
                null,
                SipsvcMessage.ResultCode.INTERNAL_ERROR,
                errorMessage
        );
        
        getConnectionManagementService().sendToSipsvc(connectionId, errorResponse);
    }
    
    /**
     * ConnectionManagementService 지연 로딩 (순환 참조 방지)
     */
    private ConnectionManagementService getConnectionManagementService() {
        if (connectionManagementService == null) {
            connectionManagementService = applicationContext.getBean(ConnectionManagementService.class);
        }
        return connectionManagementService;
    }
}
