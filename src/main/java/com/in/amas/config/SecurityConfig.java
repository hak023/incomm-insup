package com.in.amas.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 보안 설정 클래스
 * application-test.yaml의 security 설정을 매핑
 * 
 * @author InComm
 * @version 1.0.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "security")
public class SecurityConfig {
    
    /**
     * 허용된 클라이언트 목록
     */
    private List<AllowedClient> allowedClients;
    
    /**
     * 허용된 클라이언트 정보
     */
    @Data
    public static class AllowedClient {
        /**
         * 클라이언트 IP 주소
         */
        private String ip;
        
        /**
         * MAC 주소
         */
        private String mac;
        
        /**
         * 인증키
         */
        private String authKey;
        
        /**
         * 설명
         */
        private String description;
    }
}