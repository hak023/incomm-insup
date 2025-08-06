package com.in.amas.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 애플리케이션 설정 클래스
 * 
 * @author InComm
 * @version 1.0.0
 */
@Configuration
@EnableConfigurationProperties({
        SecurityConfig.class,
        TcpServerConfig.class,
        InsupcConfig.class
})
public class ApplicationConfig {
    
    /**
     * ObjectMapper Bean 설정
     * JSON 직렬화/역직렬화를 위한 설정
     * 
     * @return 설정된 ObjectMapper
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        
        // 프로퍼티 명명 전략 설정 (snake_case)
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        
        // 알 수 없는 프로퍼티 무시
        mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        
        // null 값 무시
        mapper.configure(com.fasterxml.jackson.databind.SerializationFeature.WRITE_NULL_MAP_VALUES, false);
        
        // 빈 객체 직렬화 허용
        mapper.configure(com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        
        return mapper;
    }
}