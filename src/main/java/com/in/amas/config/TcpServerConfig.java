package com.in.amas.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * TCP 서버 설정 클래스
 * application-test.yaml의 tcp.server 설정을 매핑
 * 
 * @author InComm
 * @version 1.0.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "tcp.server")
public class TcpServerConfig {
    
    /**
     * 서버 포트
     */
    private int port = 9090;
    
    /**
     * Boss 스레드 수
     */
    private int bossThreads = 1;
    
    /**
     * Worker 스레드 수
     */
    private int workerThreads = 4;
    
    /**
     * SO_BACKLOG 값
     */
    private int soBacklog = 128;
    
    /**
     * SO_KEEPALIVE 설정
     */
    private boolean soKeepalive = true;
    
    /**
     * TCP_NODELAY 설정
     */
    private boolean tcpNodelay = true;
    
    /**
     * 연결 타임아웃 (밀리초)
     */
    private long connectionTimeout = 7200000; // 2시간
    
    /**
     * 최대 연결 수
     */
    private int maxConnections = 100;
}