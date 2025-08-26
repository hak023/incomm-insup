package com.in.amas.insupclient.service;

import com.in.amas.insupclient.dto.ClientConnectionInfo;
import com.in.amas.insupclient.dto.SipsvcMessage;
import com.in.amas.insupclient.dto.InsupcMessage;
import com.in.amas.insupclient.config.SecurityConfig;
import com.in.amas.insupclient.tcp.SipsvcTcpServer;
import com.in.amas.insupclient.tcp.InsupcTcpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
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
public class ConnectionManagementService {
    
    @Value("${tcp.server.connection-timeout:7200000}")  // 2시간
    private long connectionTimeout;
    
    private final SecurityConfig securityConfig;
    private final ApplicationContext applicationContext;
    
    // SipsvcTcpServer와 InsupcTcpClient는 순환 참조 방지를 위해 지연 로딩
    private SipsvcTcpServer sipsvcTcpServer;
    private InsupcTcpClient insupcTcpClient;
    
    public ConnectionManagementService(SecurityConfig securityConfig,
                                     ApplicationContext applicationContext) {
        this.securityConfig = securityConfig;
        this.applicationContext = applicationContext;
    }
    
    // 클라이언트 연결 정보 저장
    private final Map<String, ClientConnectionInfo> clientConnections = new ConcurrentHashMap<>();
    
    // 연결 정리용 스케줄러
    private ScheduledExecutorService connectionCleanupScheduler;
    

    
    @PostConstruct
    public void initialize() {
        log.info("ConnectionManagementService initialized - connection timeout: {}ms", connectionTimeout);
        
        // 연결 정리 스케줄러 시작 (매 1분마다 실행)
        connectionCleanupScheduler = Executors.newScheduledThreadPool(1);
        connectionCleanupScheduler.scheduleWithFixedDelay(
                this::cleanupTimedOutConnections,
                60,  // 초기 지연
                60,  // 실행 간격
                TimeUnit.SECONDS
        );
        
        log.info("Connection timeout cleanup scheduler started");
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
        
        log.info("New client connection registered - ID: {}, IP: {}, Port: {}", 
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
            log.info("Client connection unregistered - ID: {}, IP: {}, duration: {}ms, total requests: {}", 
                    connectionId, 
                    connectionInfo.getClientIp(),
                    connectionInfo.getConnectionDuration(),
                    connectionInfo.getTotalRequests());
        } else {
            log.warn("Connection info not found for unregistration - ID: {}", connectionId);
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
        log.info("Client authentication attempt - IP: {}, MAC: {}", clientIp, macAddress);
        
        List<SecurityConfig.AllowedClient> allowedClients = securityConfig.getAllowedClients();
        
        // allowedClients가 null이거나 비어있으면 인증 실패
        if (allowedClients == null || allowedClients.isEmpty()) {
            log.warn("No allowed clients configured - authentication failed for IP: {}, MAC: {}", clientIp, macAddress);
            return false;
        }
        
        boolean isAuthenticated = allowedClients.stream()
                .anyMatch(client -> 
                        client.getIp().equals(clientIp) &&
                        client.getMac().equals(macAddress) &&
                        client.getAuthKey().equals(authKey)
                );
        
        if (isAuthenticated) {
            log.info("Client authentication successful - IP: {}, MAC: {}", clientIp, macAddress);
        } else {
            log.warn("Client authentication failed - IP: {}, MAC: {}", clientIp, macAddress);
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
            
            log.info("Client authentication status updated - ID: {}, authenticated: {}", connectionId, authenticated);
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
            log.debug("Client heartbeat updated - ID: {}", connectionId);
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
        log.info("Sending message to sipsvc - connection ID: {}, type: {}", connectionId, message.getType());
        
        try {
            getSipsvcTcpServer().sendMessage(connectionId, message);
            log.debug("sipsvc message sent successfully - connection ID: {}, result code: {}", 
                    connectionId, message.getResultCode());
        } catch (Exception e) {
            log.error("sipsvc message send failed - connection ID: {}, error: {}", 
                    connectionId, e.getMessage(), e);
        }
    }
    
    /**
     * INSUPC로 메시지 전송
     * 
     * @param message 전송할 메시지
     * @param requestId 요청 ID
     */
    public void sendToInsupc(InsupcMessage message, String requestId) {
        log.info("Sending message to INSUPC - request ID: {}, code: {}", requestId, message.getMsgCode());
        
        try {
            boolean success = getInsupcTcpClient().sendMessage(message, requestId);
            if (success) {
                log.debug("INSUPC message sent successfully - request ID: {}, session ID: {}", 
                        requestId, message.getSessionId());
            } else {
                log.error("INSUPC message send failed - request ID: {}, connection unavailable", requestId);
            }
        } catch (Exception e) {
            log.error("INSUPC message send failed - request ID: {}, error: {}", 
                    requestId, e.getMessage(), e);
        }
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
                
                log.warn("Connection timed out and unregistered - ID: {}, IP: {}, last activity: {}ms ago", 
                        connectionId, 
                        connectionInfo.getClientIp(),
                        currentTime - connectionInfo.getLastActivityTime());
                
                // 연결 해제
                unregisterConnection(connectionId);
                
                // 실제 TCP 연결 종료
                getSipsvcTcpServer().closeConnection(connectionId);
            }
            
            if (!timedOutConnections.isEmpty()) {
                log.info("Timed out connections cleanup completed - {} connections unregistered", timedOutConnections.size());
            }
            
        } catch (Exception e) {
            log.error("Error during connection timeout cleanup: {}", e.getMessage(), e);
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
        log.info("ConnectionManagementService shutdown started");
        
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
        
        // Log all connection information
        log.info("Active connections count: {}", clientConnections.size());
        clientConnections.forEach((id, info) -> 
                log.info("Connection info - ID: {}, IP: {}, duration: {}ms, requests: {}", 
                        id, info.getClientIp(), info.getConnectionDuration(), info.getTotalRequests()));
        
        clientConnections.clear();
        
        log.info("ConnectionManagementService shutdown completed");
    }
    
    /**
     * SipsvcTcpServer 지연 로딩 (순환 참조 방지)
     */
    private SipsvcTcpServer getSipsvcTcpServer() {
        if (sipsvcTcpServer == null) {
            sipsvcTcpServer = applicationContext.getBean(SipsvcTcpServer.class);
        }
        return sipsvcTcpServer;
    }
    
    /**
     * InsupcTcpClient 지연 로딩 (순환 참조 방지)
     */
    private InsupcTcpClient getInsupcTcpClient() {
        if (insupcTcpClient == null) {
            insupcTcpClient = applicationContext.getBean(InsupcTcpClient.class);
        }
        return insupcTcpClient;
    }
}
