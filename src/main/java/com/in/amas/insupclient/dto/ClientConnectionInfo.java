package com.in.amas.insupclient.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 클라이언트 연결 정보를 관리하는 DTO
 * 
 * @author InComm
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientConnectionInfo {
    
    /**
     * 연결 ID (고유 식별자)
     */
    private String connectionId;
    
    /**
     * 클라이언트 IP 주소
     */
    private String clientIp;
    
    /**
     * 클라이언트 포트
     */
    private int clientPort;
    
    /**
     * MAC 주소
     */
    private String macAddress;
    
    /**
     * 인증키
     */
    private String authKey;
    
    /**
     * 세션 ID
     */
    private String sessionId;
    
    /**
     * 연결 시간
     */
    private long connectionTime;
    
    /**
     * 마지막 활동 시간
     */
    private long lastActivityTime;
    
    /**
     * 인증 상태
     */
    private boolean authenticated;
    
    /**
     * 연결 활성화 상태
     */
    private boolean active;
    
    /**
     * 총 요청 수
     */
    private long totalRequests;
    
    /**
     * 마지막 heartbeat 시간
     */
    private long lastHeartbeatTime;
    
    /**
     * 클라이언트 설명
     */
    private String description;
    
    /**
     * 연결 활동 시간 업데이트
     */
    public void updateActivity() {
        this.lastActivityTime = System.currentTimeMillis();
    }
    
    /**
     * 요청 수 증가
     */
    public void incrementRequests() {
        this.totalRequests++;
        updateActivity();
    }
    
    /**
     * heartbeat 시간 업데이트
     */
    public void updateHeartbeat() {
        this.lastHeartbeatTime = System.currentTimeMillis();
        updateActivity();
    }
    
    /**
     * 연결이 타임아웃되었는지 확인
     * @param timeoutMillis 타임아웃 시간 (밀리초)
     * @return 타임아웃 여부
     */
    public boolean isTimedOut(long timeoutMillis) {
        return (System.currentTimeMillis() - lastActivityTime) > timeoutMillis;
    }
    
    /**
     * 연결 지속 시간 조회
     * @return 연결 지속 시간 (밀리초)
     */
    public long getConnectionDuration() {
        return System.currentTimeMillis() - connectionTime;
    }
}
