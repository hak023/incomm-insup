package com.in.amas.insupclient.protocol;

import com.in.amas.insupclient.dto.InsupcMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * INSUPC와의 TCP 프로토콜 파서
 * 로그에서 확인된 바이너리 프로토콜을 기반으로 구현
 * 
 * @author InComm
 * @version 1.0.0
 */
@Slf4j
@Component
public class InsupcProtocolParser {
    
    // INSUP 프로토콜 상수 (C++ 구현과 동일)
    private static final int INSUP_HEADER_SIZE = 62; // 고정 헤더 크기
    
    // 헤더 필드 크기 정의
    private static final int INSUP_HEADER_MSG_LEN_SIZE = 2;
    private static final int INSUP_HEADER_MSG_CODE_SIZE = 1;
    private static final int INSUP_HEADER_SVCA_SIZE = 1;
    private static final int INSUP_HEADER_DVCA_SIZE = 1;
    private static final int INSUP_HEADER_INAS_ID_SIZE = 1;
    private static final int INSUP_HEADER_SESSION_ID_SIZE = 30;
    private static final int INSUP_HEADER_SVC_ID_SIZE = 4;
    private static final int INSUP_HEADER_RESULT_SIZE = 1;
    private static final int INSUP_HEADER_WTIME_SIZE = 17;
    private static final int INSUP_HEADER_MAJOR_VERSION_SIZE = 1;
    private static final int INSUP_HEADER_MINOR_VERSION_SIZE = 1;
    private static final int INSUP_HEADER_DUMMY_SIZE = 1;
    private static final int INSUP_HEADER_ACK_SIZE = 1;
    
    /**
     * 바이트 배열을 InsupcMessage로 파싱
     * 
     * @param data 바이트 배열
     * @return 파싱된 InsupcMessage
     * @throws Exception 파싱 에러
     */
    public InsupcMessage parseMessage(byte[] data) throws Exception {
        try {
            log.debug(">>> INSUPC message received - size: {} bytes", data.length);
            
            ByteBuffer buffer = ByteBuffer.wrap(data);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            
            // 헤더 파싱 (C++ struct _t_insup_message_header와 동일)
            
            // MSG_LEN (2바이트) - Little Endian 변환
            int msgLen = Short.toUnsignedInt(buffer.getShort());
            
            // MSG_CODE (1바이트)
            int msgCode = buffer.get() & 0xFF;
            
            // SVCA (1바이트)
            int svca = buffer.get() & 0xFF;
            
            // DVCA (1바이트)
            int dvca = buffer.get() & 0xFF;
            
            // INAS_ID (1바이트)
            int inasId = buffer.get() & 0xFF;
            
            // SESSION_ID (30바이트 고정)
            byte[] sessionBytes = new byte[INSUP_HEADER_SESSION_ID_SIZE];
            buffer.get(sessionBytes);
            String sessionId = new String(sessionBytes, StandardCharsets.UTF_8).trim();
            
            // SVC_ID (4바이트 고정)
            byte[] svcBytes = new byte[INSUP_HEADER_SVC_ID_SIZE];
            buffer.get(svcBytes);
            String svcId = new String(svcBytes, StandardCharsets.UTF_8).trim();
            
            // RESULT (1바이트)
            int result = buffer.get() & 0xFF;
            
            // WTIME (17바이트 고정)
            byte[] wtimeBytes = new byte[INSUP_HEADER_WTIME_SIZE];
            buffer.get(wtimeBytes);
            String wtime = new String(wtimeBytes, StandardCharsets.UTF_8).trim();
            
            // MAJOR_VERSION (1바이트)
            int majorVersion = buffer.get() & 0xFF;
            
            // MINOR_VERSION (1바이트)
            int minorVersion = buffer.get() & 0xFF;
            
            // DUMMY (1바이트)
            int dummy = buffer.get() & 0xFF;
            
            // USE_REQUEST_ACK (1바이트)
            int useRequestAck = buffer.get() & 0xFF;
            
            // 바디 파라미터 파싱 (헤더 이후 바디 부분)
            int paramCount = buffer.get() & 0xFF;  // 파라미터 개수 (1바이트)
            
            // 파라미터 파싱
            List<InsupcMessage.InsupcParameter> parameters = parseParameters(buffer, paramCount);
            
            InsupcMessage message = InsupcMessage.builder()
                    .msgLen(msgLen)
                    .msgCode(msgCode)
                    .svca(svca)
                    .dvca(dvca)
                    .inasId(inasId)
                    .sessionId(sessionId)
                    .svcId(svcId)
                    .result(result)
                    .wtime(wtime)
                    .majorVersion(majorVersion)
                    .minorVersion(minorVersion)
                    .dummy(dummy)
                    .useRequestAck(useRequestAck)
                    .parameters(parameters)
                    .build();
            
            log.info(">>> INSUPC message parsing completed - code: {}, session: {}, parameter count: {}", 
                    msgCode, sessionId, paramCount);
            
            return message;
            
        } catch (Exception e) {
            log.error(">>> INSUPC message parsing failed: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * InsupcMessage를 바이트 배열로 직렬화
     * 
     * @param message InsupcMessage 객체
     * @return 바이트 배열
     * @throws Exception 직렬화 에러
     */
    public byte[] serializeMessage(InsupcMessage message) throws Exception {
        try {
            ByteBuffer buffer = ByteBuffer.allocate(8192);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            
            // 헤더 구성 (C++ struct _t_insup_message_header와 동일)
            
            // MSG_LEN (2바이트) - 나중에 설정
            buffer.putShort((short) 0);
            
            // MSG_CODE (1바이트)
            buffer.put((byte) message.getMsgCode());
            
            // SVCA (1바이트)
            buffer.put((byte) message.getSvca());
            
            // DVCA (1바이트)
            buffer.put((byte) message.getDvca());
            
            // INAS_ID (1바이트)
            buffer.put((byte) message.getInasId());
            
            // SESSION_ID (30바이트 고정)
            writeFixedString(buffer, message.getSessionId(), INSUP_HEADER_SESSION_ID_SIZE);
            
            // SVC_ID (4바이트 고정)
            writeFixedString(buffer, message.getSvcId(), INSUP_HEADER_SVC_ID_SIZE);
            
            // RESULT (1바이트)
            buffer.put((byte) message.getResult());
            
            // WTIME (17바이트 고정)
            writeFixedString(buffer, message.getWtime() != null ? message.getWtime() : "", INSUP_HEADER_WTIME_SIZE);
            
            // MAJOR_VERSION (1바이트)
            buffer.put((byte) (message.getMajorVersion() != 0 ? message.getMajorVersion() : 1));
            
            // MINOR_VERSION (1바이트)
            buffer.put((byte) (message.getMinorVersion() != 0 ? message.getMinorVersion() : 0));
            
            // DUMMY (1바이트)
            buffer.put((byte) message.getDummy());
            
            // USE_REQUEST_ACK (1바이트)
            buffer.put((byte) (message.getUseRequestAck() != 0 ? message.getUseRequestAck() : InsupcMessage.RequestAck.DONT_USE_REQUEST_ACK));
            
            // 파라미터 개수 (1바이트)
            int paramCount = message.getParameters() != null ? message.getParameters().size() : 0;
            buffer.put((byte) paramCount);
            
            // 파라미터 직렬화
            if (message.getParameters() != null) {
                for (InsupcMessage.InsupcParameter param : message.getParameters()) {
                    serializeParameter(buffer, param);
                }
            }
            
            // 전체 크기 설정 (헤더 제외한 바디 크기)
            int totalSize = buffer.position();
            int bodySize = totalSize - INSUP_HEADER_SIZE;
            buffer.putShort(0, (short) bodySize);  // MSG_LEN 필드에 바디 크기 설정
            
            byte[] result = new byte[totalSize];
            buffer.flip();
            buffer.get(result);
            
            log.info("<<< INSUPC message serialization completed - code: {}, session: {}, total size: {} bytes, body size: {} bytes", 
                    message.getMsgCode(), message.getSessionId(), totalSize, bodySize);
            
            return result;
            
        } catch (Exception e) {
            log.error("<<< INSUPC message serialization failed: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 로그온 요청 메시지 생성 (C++ 구현과 동일)
     * 
     * @param inasId INAS ID
     * @return 로그온 요청 메시지
     */
    public InsupcMessage createLogonRequest(int inasId) {
        return createDbAccessRequest(inasId);
    }
    
    /**
     * DB 접근 요청 메시지 생성 (C++ 구현과 동일)
     * 
     * @param inasId INAS ID
     * @return DB 접근 요청 메시지
     */
    public InsupcMessage createDbAccessRequest(int inasId) {
        String sessionId = generateSessionId();
        
        // DB 로그온 정보 파라미터 생성 (C++ struct _t_insup_body_logon_info와 동일)
        List<InsupcMessage.InsupcParameter> parameters = new ArrayList<>();
        parameters.add(InsupcMessage.InsupcParameter.builder()
                .type(InsupcMessage.InsupcParameter.Type.DB_LOGON_INFO)
                .size(8)  // VCA(1) + INAS_ID(1) + MODULE_NAME(1) + NET_CONNECT_ID(1) + IP(4) = 8bytes
                .value(new byte[]{
                    (byte)0xF0,           // VCA 고정값
                    (byte)inasId,         // INAS ID
                    (byte)0x01,           // Module Name
                    (byte)0x03,           // Net Connect ID
                    127, 0, 0, 1          // IP: 127.0.0.1
                })
                .build());
        
        return InsupcMessage.builder()
                .msgCode(InsupcMessage.MessageCode.DB_ACCESS_REQUEST)
                .svca(InsupcMessage.VCA.DEFAULT_SVCA)
                .dvca(InsupcMessage.VCA.DEFAULT_DVCA)
                .inasId(inasId)
                .sessionId(sessionId)
                .svcId("TEST")
                .result(0)
                .wtime("")
                .majorVersion(1)
                .minorVersion(0)
                .dummy(0)
                .useRequestAck(InsupcMessage.RequestAck.DONT_USE_REQUEST_ACK)
                .parameters(parameters)
                .build();
    }
    
    /**
     * 질의 요청 메시지 생성 (C++ 구현과 동일)
     * 
     * @param apiName API 이름 (예: mcidPstnGetInfoV2)
     * @param inputValues 입력 파라미터 배열
     * @param inasId INAS ID
     * @return 질의 요청 메시지
     */
    public InsupcMessage createQueryRequest(String apiName, List<String> inputValues, int inasId) {
        String sessionId = generateSessionId();
        
        List<InsupcMessage.InsupcParameter> parameters = new ArrayList<>();
        
        // DB Operation Name 파라미터 (C++ generate_insup_db_operation_name_parameter와 동일)
        parameters.add(InsupcMessage.InsupcParameter.builder()
                .type(InsupcMessage.InsupcParameter.Type.DB_OPERATION_NAME)
                .size(apiName.length() + 1)  // 문자열 길이 + 길이 바이트(1)
                .value(apiName)
                .build());
        
        // SQL Input 파라미터 (C++ generate_insup_sql_input_parameter와 동일)
        parameters.add(InsupcMessage.InsupcParameter.builder()
                .type(InsupcMessage.InsupcParameter.Type.SQL_INPUT)
                .size(calculateSqlInputSize(inputValues))
                .value(inputValues)
                .build());
        
        return InsupcMessage.builder()
                .msgCode(InsupcMessage.MessageCode.DB_QUERY_REQUEST)
                .svca(InsupcMessage.VCA.DEFAULT_SVCA)
                .dvca(InsupcMessage.VCA.DEFAULT_DVCA)
                .inasId(inasId)
                .sessionId(sessionId)
                .svcId("TEST")
                .result(0)
                .wtime("")
                .majorVersion(1)
                .minorVersion(0)
                .dummy(0)
                .useRequestAck(InsupcMessage.RequestAck.DONT_USE_REQUEST_ACK)
                .parameters(parameters)
                .build();
    }
    
    /**
     * SQL Input 파라미터 크기 계산
     */
    private int calculateSqlInputSize(List<String> inputValues) {
        int size = 1; // 파라미터 개수 (1바이트)
        for (String value : inputValues) {
            size += 2; // 길이 필드 (2바이트)
            size += value.getBytes(StandardCharsets.UTF_8).length; // 값
        }
        return size;
    }
    
    /**
     * 파라미터 목록 파싱
     */
    private List<InsupcMessage.InsupcParameter> parseParameters(ByteBuffer buffer, int paramCount) {
        List<InsupcMessage.InsupcParameter> parameters = new ArrayList<>();
        
        for (int i = 0; i < paramCount; i++) {
            // 파라미터 헤더 파싱 (C++ struct _t_insup_body_parameter)
            int type = buffer.get() & 0xFF;        // TYPE (1바이트)
            int size = Short.toUnsignedInt(buffer.getShort()); // LENGTH (2바이트)
            
            // 파라미터 값 파싱
            byte[] valueBytes = new byte[size];
            buffer.get(valueBytes);
            
            Object value = parseParameterValueFromBytes(type, valueBytes);
            
            InsupcMessage.InsupcParameter param = InsupcMessage.InsupcParameter.builder()
                    .type(type)
                    .size(size)
                    .value(value)
                    .build();
            
            parameters.add(param);
        }
        
        return parameters;
    }
    
    /**
     * 파라미터 값 파싱 (C++ 구현과 동일)
     */
    private Object parseParameterValueFromBytes(int type, byte[] valueBytes) {
        ByteBuffer valueBuffer = ByteBuffer.wrap(valueBytes);
        valueBuffer.order(ByteOrder.LITTLE_ENDIAN);
        
        switch (type) {
            case InsupcMessage.InsupcParameter.Type.DB_OPERATION_ID:
                // MODULE_ID (2바이트)
                return Short.toUnsignedInt(valueBuffer.getShort());
                
            case InsupcMessage.InsupcParameter.Type.DB_OPERATION_NAME:
                // SIZE(1) + NAME(M)
                int nameSize = valueBuffer.get() & 0xFF;
                byte[] nameBytes = new byte[nameSize];
                valueBuffer.get(nameBytes);
                return new String(nameBytes, StandardCharsets.UTF_8);
                
            case InsupcMessage.InsupcParameter.Type.SQL_INPUT:
            case InsupcMessage.InsupcParameter.Type.SQL_OUTPUT:
                // PARAM_COUNT(1) + [SIZE(2) + VALUE(N)]*
                int paramCount = valueBuffer.get() & 0xFF;
                List<String> params = new ArrayList<>();
                for (int i = 0; i < paramCount; i++) {
                    int paramSize = Short.toUnsignedInt(valueBuffer.getShort());
                    byte[] paramBytes = new byte[paramSize];
                    valueBuffer.get(paramBytes);
                    params.add(new String(paramBytes, StandardCharsets.UTF_8));
                }
                return params;
                
            case InsupcMessage.InsupcParameter.Type.SQL_RESULT:
                // RESULT_CATEGORY(1) + RESULT_VALUE(1)
                if (valueBytes.length >= 2) {
                    return new byte[]{valueBytes[0], valueBytes[1]};
                }
                return valueBytes;
                
            case InsupcMessage.InsupcParameter.Type.DB_STATUS:
                // SYSTEM_CATEGORY(1) + SYSTEM_STATUS(1)
                if (valueBytes.length >= 2) {
                    return new String(valueBytes, StandardCharsets.UTF_8);
                }
                return valueBytes;
                
            case InsupcMessage.InsupcParameter.Type.DB_LOGON_INFO:
                // VCA(1) + INAS_ID(1) + MODULE_NAME(1) + NET_CONNECT_ID(1) + IP(4)
                return valueBytes;
                
            default:
                return valueBytes;
        }
    }
    
    /**
     * 파라미터 값 파싱 (기존 메서드 - 호환성 유지)
     */
    private Object parseParameterValue(ByteBuffer buffer, int type) {
        switch (type) {
            case InsupcMessage.InsupcParameter.Type.DB_OPERATION_NAME:
            case InsupcMessage.InsupcParameter.Type.DB_STATUS:
                int strLen = buffer.getInt();
                byte[] strBytes = new byte[strLen];
                buffer.get(strBytes);
                return new String(strBytes, StandardCharsets.UTF_8);
                
            case InsupcMessage.InsupcParameter.Type.SQL_INPUT:
            case InsupcMessage.InsupcParameter.Type.SQL_OUTPUT:
                int inputCount = buffer.getInt();
                List<String> inputs = new ArrayList<>();
                for (int i = 0; i < inputCount; i++) {
                    int inputLen = buffer.getInt();
                    byte[] inputBytes = new byte[inputLen];
                    buffer.get(inputBytes);
                    inputs.add(new String(inputBytes, StandardCharsets.UTF_8));
                }
                return inputs;
                
            case InsupcMessage.InsupcParameter.Type.DB_LOGON_INFO:
            case InsupcMessage.InsupcParameter.Type.SQL_RESULT:
                int dataLen = buffer.getInt();
                byte[] dataBytes = new byte[dataLen];
                buffer.get(dataBytes);
                return dataBytes;
                
            default:
                // 알 수 없는 타입의 경우 남은 데이터를 모두 읽음
                byte[] remainingBytes = new byte[buffer.remaining()];
                buffer.get(remainingBytes);
                return remainingBytes;
        }
    }
    
    /**
     * 파라미터 직렬화 (C++ 구현과 동일)
     */
    private void serializeParameter(ByteBuffer buffer, InsupcMessage.InsupcParameter param) {
        // 파라미터 헤더 (TYPE: 1바이트, LENGTH: 2바이트)
        buffer.put((byte) param.getType());
        
        byte[] valueBytes = serializeParameterValue(param);
        buffer.putShort((short) valueBytes.length);
        buffer.put(valueBytes);
    }
    
    /**
     * 파라미터 값 직렬화 (C++ 구현과 동일)
     */
    private byte[] serializeParameterValue(InsupcMessage.InsupcParameter param) {
        ByteBuffer valueBuffer = ByteBuffer.allocate(1024);
        valueBuffer.order(ByteOrder.LITTLE_ENDIAN);
        
        switch (param.getType()) {
            case InsupcMessage.InsupcParameter.Type.DB_OPERATION_ID:
                // MODULE_ID (2바이트)
                if (param.getValue() instanceof Integer) {
                    valueBuffer.putShort(((Integer) param.getValue()).shortValue());
                }
                break;
                
            case InsupcMessage.InsupcParameter.Type.DB_OPERATION_NAME:
                // SIZE(1) + NAME(M)
                if (param.getValue() instanceof String) {
                    String name = (String) param.getValue();
                    byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
                    valueBuffer.put((byte) nameBytes.length);
                    valueBuffer.put(nameBytes);
                }
                break;
                
            case InsupcMessage.InsupcParameter.Type.SQL_INPUT:
                // PARAM_COUNT(1) + [SIZE(2) + VALUE(N)]*
                if (param.getValue() instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<String> inputs = (List<String>) param.getValue();
                    valueBuffer.put((byte) inputs.size());
                    for (String input : inputs) {
                        byte[] inputBytes = input.getBytes(StandardCharsets.UTF_8);
                        valueBuffer.putShort((short) inputBytes.length);
                        valueBuffer.put(inputBytes);
                    }
                }
                break;
                
            case InsupcMessage.InsupcParameter.Type.SQL_OUTPUT:
                // FIELD_COUNT(1) + [SIZE(2) + VALUE(N)]*
                if (param.getValue() instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<String> outputs = (List<String>) param.getValue();
                    valueBuffer.put((byte) outputs.size());
                    for (String output : outputs) {
                        byte[] outputBytes = output.getBytes(StandardCharsets.UTF_8);
                        valueBuffer.putShort((short) outputBytes.length);
                        valueBuffer.put(outputBytes);
                    }
                }
                break;
                
            default:
                if (param.getValue() instanceof byte[]) {
                    valueBuffer.put((byte[]) param.getValue());
                } else if (param.getValue() instanceof String) {
                    valueBuffer.put(((String) param.getValue()).getBytes(StandardCharsets.UTF_8));
                }
                break;
        }
        
        byte[] result = new byte[valueBuffer.position()];
        valueBuffer.flip();
        valueBuffer.get(result);
        return result;
    }
    
    /**
     * 고정 길이 문자열 쓰기
     */
    private void writeFixedString(ByteBuffer buffer, String str, int length) {
        byte[] strBytes = str != null ? str.getBytes(StandardCharsets.UTF_8) : new byte[0];
        byte[] fixedBytes = new byte[length];
        
        int copyLength = Math.min(strBytes.length, length);
        System.arraycopy(strBytes, 0, fixedBytes, 0, copyLength);
        
        buffer.put(fixedBytes);
    }
    
    /**
     * 세션 ID 생성
     */
    private String generateSessionId() {
        return String.valueOf(System.currentTimeMillis()).substring(0, 13);
    }
}
