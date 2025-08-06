package com.in.amas.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * INSUPC와의 TCP 통신을 위한 메시지 DTO
 * 로그에서 확인된 구조를 기반으로 구현
 * 
 * @author InComm
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InsupcMessage {
    
    /**
     * 메시지 크기
     */
    private int size;
    
    /**
     * 메시지 코드 (1:Query Request, 2:Query Response, 3:Logon Request, 4:Logon Response)
     */
    private int code;
    
    /**
     * Source VCA
     */
    private int svca;
    
    /**
     * Destination VCA
     */
    private int dvca;
    
    /**
     * AS ID
     */
    private int asId;
    
    /**
     * 세션 ID
     */
    private String sessionId;
    
    /**
     * 서비스 ID
     */
    private String svcId;
    
    /**
     * 결과 코드
     */
    private int result;
    
    /**
     * 대기 시간
     */
    private String wtime;
    
    /**
     * 더미 필드
     */
    private String dummy;
    
    /**
     * 파라미터 목록
     */
    private List<InsupcParameter> parameters;
    
    /**
     * 메시지 코드 상수
     */
    public static class Code {
        public static final int QUERY_REQUEST = 1;
        public static final int QUERY_RESPONSE = 2;
        public static final int LOGON_REQUEST = 3;
        public static final int LOGON_RESPONSE = 4;
    }
    
    /**
     * VCA 상수
     */
    public static class VCA {
        public static final int SOURCE = 0xf0;
        public static final int DESTINATION = 0xb1;
    }
    
    /**
     * INSUPC 파라미터 클래스
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InsupcParameter {
        /**
         * 파라미터 타입
         * 2: Operation Name
         * 3: Sql Input  
         * 4: Sql Output
         * 5: Sql Result
         * 6: DB Status
         * 7: Logon Info
         */
        private int type;
        
        /**
         * 파라미터 값
         */
        private Object value;
        
        /**
         * 파라미터 타입 상수
         */
        public static class Type {
            public static final int OPERATION_NAME = 2;
            public static final int SQL_INPUT = 3;
            public static final int SQL_OUTPUT = 4;
            public static final int SQL_RESULT = 5;
            public static final int DB_STATUS = 6;
            public static final int LOGON_INFO = 7;
        }
    }
}