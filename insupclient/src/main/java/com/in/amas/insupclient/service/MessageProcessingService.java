package com.in.amas.insupclient.service;

import com.in.amas.insupclient.dto.WorkerMessage;
import com.in.amas.insupclient.dto.SipsvcMessage;
import com.in.amas.insupclient.dto.InsupcMessage;
import com.in.amas.insupclient.protocol.SipsvcProtocolParser;
import com.in.amas.insupclient.protocol.InsupcProtocolParser;
import com.in.amas.insupclient.tcp.SipsvcTcpServer;
import com.in.amas.insupclient.tcp.InsupcTcpClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@RequiredArgsConstructor
public class MessageProcessingService {
    
    private final SipsvcProtocolParser sipsvcProtocolParser;
    private final InsupcProtocolParser insupcProtocolParser;
    private final ConnectionManagementService connectionManagementService;
    
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
        
        log.info("sipsvc 요청 처리 시작 - 연결 ID: {}, 요청 ID: {}, 타입: {}", 
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
                    log.warn("알 수 없는 sipsvc 메시지 타입: {}", sipsvcMessage.getType());
                    sendErrorResponse(sipsvcMessage, connectionId, "Unknown message type");
            }
            
        } catch (Exception e) {
            log.error("sipsvc 요청 처리 중 오류 - 연결 ID: {}, 요청 ID: {}, 오류: {}", 
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
        
        log.info("INSUPC 응답 처리 시작 - 요청 ID: {}, 코드: {}", 
                requestId, insupcMessage.getMsgCode());
        
        try {
            // 요청 ID로 원래 연결 찾기
            String connectionId = requestConnectionMap.remove(requestId);
            
            if (connectionId == null) {
                log.warn("INSUPC 응답에 대응하는 연결을 찾을 수 없음 - 요청 ID: {}", requestId);
                return;
            }
            
            // INSUPC 응답을 sipsvc 응답으로 변환
            SipsvcMessage responseMessage = convertInsupcToSipsvc(insupcMessage, requestId);
            
            // sipsvc로 응답 전송
            connectionManagementService.sendToSipsvc(connectionId, responseMessage);
            
            log.info("INSUPC 응답을 sipsvc로 전달 완료 - 연결 ID: {}, 요청 ID: {}", 
                    connectionId, requestId);
            
        } catch (Exception e) {
            log.error("INSUPC 응답 처리 중 오류 - 요청 ID: {}, 오류: {}", 
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
        log.error("메시지 처리 최종 실패 - 요청 ID: {}, 타입: {}, 오류: {}", 
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
            
            connectionManagementService.sendToSipsvc(
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
        log.info("인증 요청 처리 - 연결 ID: {}, 클라이언트 IP: {}", connectionId, request.getClientIp());
        
        // 인증 검증
        boolean isAuthenticated = connectionManagementService.authenticateClient(
                request.getClientIp(),
                request.getMacAddress(),
                request.getAuthKey()
        );
        
        SipsvcMessage response;
        if (isAuthenticated) {
            response = sipsvcProtocolParser.createAuthResponse(request, true, "Authentication successful");
            connectionManagementService.updateClientAuthentication(connectionId, true);
            log.info("클라이언트 인증 성공 - 연결 ID: {}", connectionId);
        } else {
            response = sipsvcProtocolParser.createAuthResponse(request, false, "Authentication failed");
            log.warn("클라이언트 인증 실패 - 연결 ID: {}, IP: {}", connectionId, request.getClientIp());
        }
        
        connectionManagementService.sendToSipsvc(connectionId, response);
    }
    
    /**
     * heartbeat 요청 처리
     */
    private void handleHeartbeatRequest(SipsvcMessage request, String connectionId) {
        log.debug("Heartbeat 요청 처리 - 연결 ID: {}", connectionId);
        
        connectionManagementService.updateClientHeartbeat(connectionId);
        
        SipsvcMessage response = sipsvcProtocolParser.createHeartbeatResponse(request);
        connectionManagementService.sendToSipsvc(connectionId, response);
    }
    
    /**
     * execute 요청 처리 (INSUPC로 질의)
     */
    private void handleExecuteRequest(SipsvcMessage request, String connectionId, String requestId) {
        log.info("Execute 요청 처리 - 연결 ID: {}, 요청 ID: {}, 전화번호: {}", 
                connectionId, requestId, request.getPhoneNumber());
        
        // 연결이 인증되었는지 확인
        if (!connectionManagementService.isClientAuthenticated(connectionId)) {
            log.warn("인증되지 않은 클라이언트의 execute 요청 - 연결 ID: {}", connectionId);
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
            connectionManagementService.sendToInsupc(queryRequest, requestId);
            
            log.info("INSUPC 질의 전송 완료 - 요청 ID: {}, 전화번호: {}", 
                    requestId, request.getPhoneNumber());
            
        } catch (Exception e) {
            log.error("INSUPC 질의 전송 실패 - 요청 ID: {}, 오류: {}", requestId, e.getMessage(), e);
            
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
        
        connectionManagementService.sendToSipsvc(connectionId, errorResponse);
    }
}
