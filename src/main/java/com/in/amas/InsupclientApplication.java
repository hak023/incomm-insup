package com.in.amas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Insupclient Gateway Application 메인 클래스
 * 
 * sipsvc와 INSUPC 사이의 Gateway 역할을 하는 TCP 서버 애플리케이션
 * - sipsvc로부터 JSON/TCP 메시지 수신
 * - INSUPC로 바이너리 TCP 질의 전송
 * - 비동기 WorkerThread를 통한 메시지 처리
 * - 연결 관리 및 인증 처리
 * 
 * @author InComm
 * @version 1.0.0
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class InsupclientApplication {

    public static void main(String[] args) {
        // 시스템 속성 설정 (리눅스 환경 최적화)
        System.setProperty("java.awt.headless", "true");
        System.setProperty("file.encoding", "UTF-8");
        System.setProperty("java.net.preferIPv4Stack", "true");
        
        // Netty 관련 최적화
        System.setProperty("io.netty.noUnsafe", "false");
        System.setProperty("io.netty.tryReflectionSetAccessible", "true");
        
        // 로그 출력
        System.out.println("=".repeat(60));
        System.out.println("Insupclient Gateway Server Starting...");
        System.out.println("Version: 1.0.0");
        System.out.println("Java Version: " + System.getProperty("java.version"));
        System.out.println("OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version"));
        System.out.println("=".repeat(60));
        
        SpringApplication.run(InsupclientApplication.class, args);
    }
}