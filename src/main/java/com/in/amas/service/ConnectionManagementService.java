package com.in.amas.service;

import com.in.amas.dto.ClientConnectionInfo;
import com.in.amas.dto.SipsvcMessage;
import com.in.amas.dto.InsupcMessage;
import com.in.amas.config.SecurityConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 클라이언트 연결 관리 서비스
 * sipsvc와 INSUPC 연결을 관리하고 인증/타임아웃 처리
 * 
 * @author InComm
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConnectionManagementService {
    
    @Value("${tcp.server.connection-timeout:7200000}")  // 2시간
    private long connectionTimeout;
    
    private final SecurityConfig securityConfig;
    
    // 클라이언트 연결 정보 저장
    private final Map<String, ClientConnectionInfo> clientConnections = new ConcurrentHashMap<>();
    
    // 연결 정리용 스케줄러
    private ScheduledExecutorService connectionCleanupScheduler;
    
    // TCP 서버/클라이언트 인스턴스들 (나중에 주입됨)
    private Object sipsvcTcpServer;  // SipsvcTcpServer 타입으로 나중에 변경
    private Object insupcTcpClient;  // InsupcTcpClient 타입으로 나중에 변경
    
    @PostConstruct
    public void initialize() {
        log.info("ConnectionManagementService 초기화 - 연결 타임아웃: {}ms", connectionTimeout);
        
        // 연결 정리 스케줄러 시작 (매 1분마다 실행)
        connectionCleanupScheduler = Executors.newScheduledThreadPool(1);
        connectionCleanupScheduler.scheduleWithFixedDelay(
                this::cleanupTimedOutConnections,
                60,  // 초기 지연
                60,  // 실행 간격
                TimeUnit.SECONDS
        );
        
        log.info("연결 타임아웃 정리 스케줄러 시작됨");
    }
    
    /**
     * 새로운 클라이언트 연결 등록
     * 
     * @param connectionId 연결 ID
     * @param clientIp 클라이언트 IP
     * @param clientPort 클라이언트 포트
     * @return 등록된 연결 정보
     */
    public ClientConnectionInfo registerConnection(String connectionId, String clientIp, int clientPort) {
        ClientConnectionInfo connectionInfo = ClientConnectionInfo.builder()
                .connectionId(connectionId)
                .clientIp(clientIp)
                .clientPort(clientPort)
                .connectionTime(System.currentTimeMillis())
                .lastActivityTime(System.currentTimeMillis())
                .authenticated(false)
                .active(true)
                .totalRequests(0)
                .lastHeartbeatTime(0)
                .build();
        
        clientConnections.put(connectionId, connectionInfo);
        
        log.info("새로운 클라이언트 연결 등록 - ID: {}, IP: {}, Port: {}", 
                connectionId, clientIp, clientPort);
        
        return connectionInfo;
    }
    
    /**
     * 클라이언트 연결 해제
     * 
     * @param connectionId 연결 ID
     */
    public void unregisterConnection(String connectionId) {
        ClientConnectionInfo connectionInfo = clientConnections.remove(connectionId);
        
        if (connectionInfo != null) {
            log.info("클라이언트 연결 해제 - ID: {}, IP: {}, 지속시간: {}ms, 총 요청수: {}", 
                    connectionId, 
                    connectionInfo.getClientIp(),
                    connectionInfo.getConnectionDuration(),
                    connectionInfo.getTotalRequests());
        } else {
            log.warn("해제할 연결 정보를 찾을 수 없음 - ID: {}", connectionId);
        }
    }
    
    /**
     * 클라이언트 인증 검증
     * 
     * @param clientIp 클라이언트 IP
     * @param macAddress MAC 주소
     * @param authKey 인증키
     * @return 인증 성공 여부
     */
    public boolean authenticateClient(String clientIp, String macAddress, String authKey) {
        log.info("클라이언트 인증 시도 - IP: {}, MAC: {}", clientIp, macAddress);
        
        List<SecurityConfig.AllowedClient> allowedClients = securityConfig.getAllowedClients();
        
        boolean isAuthenticated = allowedClients.stream()
                .anyMatch(client -> 
                        client.getIp().equals(clientIp) &&
                        client.getMac().equals(macAddress) &&
                        client.getAuthKey().equals(authKey)
                );
        
        if (isAuthenticated) {
            log.info("클라이언트 인증 성공 - IP: {}, MAC: {}", clientIp, macAddress);
        } else {
            log.warn("클라이언트 인증 실패 - IP: {}, MAC: {}", clientIp, macAddress);
        }
        
        return isAuthenticated;
    }
    
    /**
     * 클라이언트 인증 상태 업데이트
     * 
     * @param connectionId 연결 ID
     * @param authenticated 인증 상태
     */
    public void updateClientAuthentication(String connectionId, boolean authenticated) {
        ClientConnectionInfo connectionInfo = clientConnections.get(connectionId);
        
        if (connectionInfo != null) {
            connectionInfo.setAuthenticated(authenticated);
            connectionInfo.updateActivity();
            
            log.info("클라이언트 인증 상태 업데이트 - ID: {}, 인증: {}", connectionId, authenticated);
        }
    }
    
    /**
     * 클라이언트 heartbeat 업데이트
     * 
     * @param connectionId 연결 ID
     */
    public void updateClientHeartbeat(String connectionId) {
        ClientConnectionInfo connectionInfo = clientConnections.get(connectionId);
        
        if (connectionInfo != null) {
            connectionInfo.updateHeartbeat();
            log.debug("클라이언트 heartbeat 업데이트 - ID: {}", connectionId);
        }
    }
    
    /**
     * 클라이언트 인증 상태 확인
     * 
     * @param connectionId 연결 ID
     * @return 인증 여부
     */
    public boolean isClientAuthenticated(String connectionId) {
        ClientConnectionInfo connectionInfo = clientConnections.get(connectionId);
        return connectionInfo != null && connectionInfo.isAuthenticated();
    }
    
    /**
     * 클라이언트 요청 수 증가
     * 
     * @param connectionId 연결 ID
     */
    public void incrementClientRequests(String connectionId) {
        ClientConnectionInfo connectionInfo = clientConnections.get(connectionId);
        
        if (connectionInfo != null) {
            connectionInfo.incrementRequests();
        }
    }
    
    /**
     * sipsvc로 메시지 전송
     * 
     * @param connectionId 연결 ID
     * @param message 전송할 메시지
     */
    public void sendToSipsvc(String connectionId, SipsvcMessage message) {
        log.info("sipsvc로 메시지 전송 - 연결 ID: {}, 타입: {}", connectionId, message.getType());
        
        // TODO: 실제 TCP 서버를 통한 메시지 전송 구현
        // sipsvcTcpServer.sendMessage(connectionId, message);
        
        // 현재는 로그만 출력
        log.debug("sipsvc 메시지 전송 완료 - 연결 ID: {}, 결과 코드: {}", 
                connectionId, message.getResultCode());
    }
    
    /**
     * INSUPC로 메시지 전송
     * 
     * @param message 전송할 메시지
     * @param requestId 요청 ID
     */
    public void sendToInsupc(InsupcMessage message, String requestId) {
        log.info("INSUPC로 메시지 전송 - 요청 ID: {}, 코드: {}", requestId, message.getCode());
        
        // TODO: 실제 TCP 클라이언트를 통한 메시지 전송 구현
        // insupcTcpClient.sendMessage(message, requestId);
        
        // 현재는 로그만 출력
        log.debug("INSUPC 메시지 전송 완료 - 요청 ID: {}, 세션 ID: {}", 
                requestId, message.getSessionId());
    }
    
    /**
     * 타임아웃된 연결 정리
     */
    public void cleanupTimedOutConnections() {
        try {
            long currentTime = System.currentTimeMillis();
            
            List<String> timedOutConnections = clientConnections.entrySet().stream()
                    .filter(entry -> entry.getValue().isTimedOut(connectionTimeout))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
            
            for (String connectionId : timedOutConnections) {
                ClientConnectionInfo connectionInfo = clientConnections.get(connectionId);
                
                log.warn("연결 타임아웃으로 해제 - ID: {}, IP: {}, 마지막 활동: {}ms 전", 
                        connectionId, 
                        connectionInfo.getClientIp(),
                        currentTime - connectionInfo.getLastActivityTime());
                
                // 연결 해제
                unregisterConnection(connectionId);
                
                // TODO: 실제 TCP 연결 종료
                // sipsvcTcpServer.closeConnection(connectionId);
            }
            
            if (!timedOutConnections.isEmpty()) {
                log.info("타임아웃된 연결 정리 완료 - {} 개 연결 해제", timedOutConnections.size());
            }
            
        } catch (Exception e) {
            log.error("연결 타임아웃 정리 중 오류: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 현재 연결 상태 조회
     * 
     * @return 연결 상태 정보
     */
    public Map<String, Object> getConnectionStatus() {
        long currentTime = System.currentTimeMillis();
        
        Map<String, Object> status = new ConcurrentHashMap<>();
        status.put("total_connections", clientConnections.size());
        
        long authenticatedCount = clientConnections.values().stream()
                .filter(ClientConnectionInfo::isAuthenticated)
                .count();
        status.put("authenticated_connections", authenticatedCount);
        
        long activeCount = clientConnections.values().stream()
                .filter(conn -> !conn.isTimedOut(connectionTimeout))
                .count();
        status.put("active_connections", activeCount);
        
        long totalRequests = clientConnections.values().stream()
                .mapToLong(ClientConnectionInfo::getTotalRequests)
                .sum();
        status.put("total_requests", totalRequests);
        
        status.put("connection_timeout_ms", connectionTimeout);
        status.put("current_time", currentTime);
        
        return status;
    }
    
    /**
     * 특정 연결 정보 조회
     * 
     * @param connectionId 연결 ID
     * @return 연결 정보 (없으면 null)
     */
    public ClientConnectionInfo getConnectionInfo(String connectionId) {
        return clientConnections.get(connectionId);
    }
    
    /**
     * 모든 연결 정보 조회
     * 
     * @return 모든 연결 정보
     */
    public Map<String, ClientConnectionInfo> getAllConnections() {
        return new ConcurrentHashMap<>(clientConnections);
    }
    
    @PreDestroy
    public void shutdown() {
        log.info("ConnectionManagementService 종료 시작");
        
        if (connectionCleanupScheduler != null && !connectionCleanupScheduler.isShutdown()) {
            connectionCleanupScheduler.shutdown();
            
            try {
                if (!connectionCleanupScheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    connectionCleanupScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                connectionCleanupScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        // 모든 연결 정보 로그 출력
        log.info("활성 연결 수: {}", clientConnections.size());
        clientConnections.forEach((id, info) -> 
                log.info("연결 정보 - ID: {}, IP: {}, 지속시간: {}ms, 요청수: {}", 
                        id, info.getClientIp(), info.getConnectionDuration(), info.getTotalRequests()));
        
        clientConnections.clear();
        
        log.info("ConnectionManagementService 종료 완료");
    }
}