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
     * 메시지 길이 (2바이트)
     */
    private int msgLen;
    
    /**
     * 메시지 코드 (1바이트) - DB_QUERY_REQUEST(1), DB_QUERY_RESPONSE(2) 등
     */
    private int msgCode;
    
    /**
     * Source VCA (1바이트)
     */
    private int svca;
    
    /**
     * Destination VCA (1바이트)
     */
    private int dvca;
    
    /**
     * INAS ID (1바이트)
     */
    private int inasId;
    
    /**
     * 세션 ID (30바이트 고정)
     */
    private String sessionId;
    
    /**
     * 서비스 ID (4바이트 고정)
     */
    private String svcId;
    
    /**
     * 결과 코드 (1바이트)
     */
    private int result;
    
    /**
     * 처리 시간 (17바이트 고정)
     */
    private String wtime;
    
    /**
     * Major 버전 (1바이트)
     */
    private int majorVersion;
    
    /**
     * Minor 버전 (1바이트)
     */
    private int minorVersion;
    
    /**
     * 더미 필드 (1바이트)
     */
    private int dummy;
    
    /**
     * Request ACK 사용 여부 (1바이트)
     */
    private int useRequestAck;
    
    /**
     * 파라미터 목록
     */
    private List<InsupcParameter> parameters;
    
    /**
     * 메시지 코드 상수 (C++ enum e_insup_header_msg_code와 동일)
     */
    public static class MessageCode {
        public static final int DB_QUERY_REQUEST = 1;
        public static final int DB_QUERY_RESPONSE = 2;
        public static final int DB_ACCESS_REQUEST = 3;
        public static final int DB_ACCESS_RESPONSE = 4;
        public static final int DB_NETTEST_REQUEST = 5;
        public static final int DB_NETTEST_RESPONSE = 6;
        public static final int DB_STATUS_REQUEST = 7;
        public static final int DB_STATUS_RESPONSE = 8;
        public static final int DB_QUERY_REQUEST_ACK = 9;
    }
    
    /**
     * VCA 상수 (C++ 구현과 동일)
     */
    public static class VCA {
        public static final int DEFAULT_VCA = 0xA2;
        public static final int DEFAULT_SVCA = 0x11;
        public static final int DEFAULT_DVCA = 0xA2;
    }
    
    /**
     * 결과 코드 상수 (C++ enum e_insup_header_result_value와 동일)
     */
    public static class ResultCode {
        public static final int SUCCESS = 0x01;
        public static final int FAIL = 0x02;
        public static final int MODULE_NOT_FOUND = 0x10;
        public static final int SENDDATA_FAIL = 0x11;
        public static final int INVALID_MESSAGE = 0x12;
        public static final int LOGIN_DENIED = 0x20;
        public static final int OVERLOAD_REJECT = 0x21;
    }
    
    /**
     * Request ACK 사용 여부 상수
     */
    public static class RequestAck {
        public static final int USE_REQUEST_ACK = 0x00;
        public static final int DONT_USE_REQUEST_ACK = 0x7F;
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
         * 파라미터 타입 (C++ enum e_insup_body_parameter_type과 동일)
         */
        private int type;
        
        /**
         * 파라미터 크기 (2바이트)
         */
        private int size;
        
        /**
         * 파라미터 값
         */
        private Object value;
        
        /**
         * 파라미터 타입 상수 (C++ enum e_insup_body_parameter_type과 동일)
         */
        public static class Type {
            public static final int DB_OPERATION_ID = 1;
            public static final int DB_OPERATION_NAME = 2;
            public static final int SQL_INPUT = 3;
            public static final int SQL_OUTPUT = 4;
            public static final int SQL_RESULT = 5;
            public static final int DB_STATUS = 6;
            public static final int DB_LOGON_INFO = 7;
        }
        
        /**
         * SQL 결과 카테고리 상수
         */
        public static class SqlResultCategory {
            public static final int SUCCESS = 0x00;
            public static final int AS_FAIL = 0x10;
            public static final int DB_FAIL = 0x20;
        }
        
        /**
         * SQL 결과 값 상수
         */
        public static class SqlResultValue {
            // SUCCESS 카테고리
            public static final int SUCCESS = 0x00;
            public static final int NO_DATA = 0x01;
            
            // AS_FAIL 카테고리
            public static final int NO_OP_ID = 0x10;
            public static final int INVALID_OP_ID = 0x11;
            public static final int NO_PARAMETER = 0x12;
            public static final int INVALID_PARAMETER = 0x13;
            public static final int NO_OP_NAME = 0x14;
            public static final int INVALID_OP_NAME = 0x15;
            
            // DB_FAIL 카테고리
            public static final int SQL_ERROR = 0x20;
            public static final int DBMS_NOT_CONNECTED = 0x21;
            public static final int DBMS_NOT_ACCESSIBLE = 0x22;
        }
    }
}