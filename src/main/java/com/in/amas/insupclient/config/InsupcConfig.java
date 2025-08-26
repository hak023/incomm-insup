package com.in.amas.insupclient.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * INSUPC 클라이언트 설정 클래스
 * application-test.yaml의 insupc 설정을 매핑
 * 
 * @author InComm
 * @version 1.0.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "insupc")
public class InsupcConfig {
    
    /**
     * INSUPC 클라이언트 목록
     */
    private List<InsupcClient> clients;
    
    /**
     * INSUPC 클라이언트 정보
     */
    @Data
    public static class InsupcClient {
        /**
         * 클라이언트 이름
         */
        private String name;
        
        /**
         * 호스트 주소
         */
        private String host;
        
        /**
         * 포트 번호
         */
        private int port;
        
        /**
         * 연결 풀 크기
         */
        private int connectionPoolSize = 5;
        
        /**
         * 연결 타임아웃 (밀리초)
         */
        private int connectionTimeout = 30000;
        
        /**
         * 읽기 타임아웃 (밀리초)
         */
        private int readTimeout = 10000;
        
        /**
         * 재시도 횟수
         */
        private int retryCount = 3;
        
        /**
         * 재시도 간격 (밀리초)
         */
        private int retryInterval = 5000;
    }
}
