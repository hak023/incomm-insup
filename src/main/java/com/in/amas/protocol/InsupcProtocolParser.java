package com.in.amas.protocol;

import com.in.amas.dto.InsupcMessage;
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
    
    private static final int HEADER_SIZE = 62; // 기본 헤더 크기
    
    /**
     * 바이트 배열을 InsupcMessage로 파싱
     * 
     * @param data 바이트 배열
     * @return 파싱된 InsupcMessage
     * @throws Exception 파싱 에러
     */
    public InsupcMessage parseMessage(byte[] data) throws Exception {
        try {
            log.debug(">>> INSUPC 메시지 수신 - 크기: {} bytes", data.length);
            
            ByteBuffer buffer = ByteBuffer.wrap(data);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            
            // 헤더 파싱
            int size = buffer.getInt();
            int code = buffer.getInt();
            int svca = buffer.get() & 0xFF;
            int dvca = buffer.get() & 0xFF;
            int asId = buffer.getInt();
            
            // 세션 ID 파싱 (13자리 문자열)
            byte[] sessionBytes = new byte[13];
            buffer.get(sessionBytes);
            String sessionId = new String(sessionBytes, StandardCharsets.UTF_8).trim();
            
            // 서비스 ID 파싱 (4자리 문자열)
            byte[] svcBytes = new byte[4];
            buffer.get(svcBytes);
            String svcId = new String(svcBytes, StandardCharsets.UTF_8).trim();
            
            int result = buffer.getInt();
            
            // Wtime 파싱 (17자리 문자열)
            byte[] wtimeBytes = new byte[17];
            buffer.get(wtimeBytes);
            String wtime = new String(wtimeBytes, StandardCharsets.UTF_8).trim();
            
            // Dummy 파싱 (나머지 헤더 부분)
            byte[] dummyBytes = new byte[HEADER_SIZE - buffer.position()];
            if (dummyBytes.length > 0) {
                buffer.get(dummyBytes);
            }
            String dummy = new String(dummyBytes, StandardCharsets.UTF_8).trim();
            
            // 파라미터 개수 파싱
            int paramCount = buffer.getInt();
            
            // 파라미터 파싱
            List<InsupcMessage.InsupcParameter> parameters = parseParameters(buffer, paramCount);
            
            InsupcMessage message = InsupcMessage.builder()
                    .size(size)
                    .code(code)
                    .svca(svca)
                    .dvca(dvca)
                    .asId(asId)
                    .sessionId(sessionId)
                    .svcId(svcId)
                    .result(result)
                    .wtime(wtime)
                    .dummy(dummy)
                    .parameters(parameters)
                    .build();
            
            log.info(">>> INSUPC 메시지 파싱 완료 - 코드: {}, 세션: {}, 파라미터 수: {}", 
                    code, sessionId, paramCount);
            
            return message;
            
        } catch (Exception e) {
            log.error(">>> INSUPC 메시지 파싱 실패: {}", e.getMessage(), e);
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
            
            // 헤더 구성
            buffer.putInt(0); // size는 나중에 설정
            buffer.putInt(message.getCode());
            buffer.put((byte) message.getSvca());
            buffer.put((byte) message.getDvca());
            buffer.putInt(message.getAsId());
            
            // 세션 ID (13자리)
            writeFixedString(buffer, message.getSessionId(), 13);
            
            // 서비스 ID (4자리)
            writeFixedString(buffer, message.getSvcId(), 4);
            
            buffer.putInt(message.getResult());
            
            // Wtime (17자리)
            writeFixedString(buffer, message.getWtime() != null ? message.getWtime() : "", 17);
            
            // Dummy 필드 (나머지 헤더 채우기)
            int currentPos = buffer.position();
            int remainingHeader = HEADER_SIZE - currentPos;
            if (remainingHeader > 0) {
                byte[] dummyData = new byte[remainingHeader];
                buffer.put(dummyData);
            }
            
            // 파라미터 개수
            int paramCount = message.getParameters() != null ? message.getParameters().size() : 0;
            buffer.putInt(paramCount);
            
            // 파라미터 직렬화
            if (message.getParameters() != null) {
                for (InsupcMessage.InsupcParameter param : message.getParameters()) {
                    serializeParameter(buffer, param);
                }
            }
            
            // 전체 크기 설정
            int totalSize = buffer.position();
            buffer.putInt(0, totalSize);
            
            byte[] result = new byte[totalSize];
            buffer.flip();
            buffer.get(result);
            
            log.info("<<< INSUPC 메시지 직렬화 완료 - 코드: {}, 세션: {}, 크기: {} bytes", 
                    message.getCode(), message.getSessionId(), totalSize);
            
            return result;
            
        } catch (Exception e) {
            log.error("<<< INSUPC 메시지 직렬화 실패: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 로그온 요청 메시지 생성
     * 
     * @param asId AS ID
     * @return 로그온 요청 메시지
     */
    public InsupcMessage createLogonRequest(int asId) {
        String sessionId = generateSessionId();
        
        // 로그온 정보 파라미터 생성
        List<InsupcMessage.InsupcParameter> parameters = new ArrayList<>();
        parameters.add(InsupcMessage.InsupcParameter.builder()
                .type(InsupcMessage.InsupcParameter.Type.LOGON_INFO)
                .value(new byte[]{(byte)0xf0, 0x01, 0x01, 0x03})
                .build());
        
        return InsupcMessage.builder()
                .code(InsupcMessage.Code.LOGON_REQUEST)
                .svca(InsupcMessage.VCA.SOURCE)
                .dvca(InsupcMessage.VCA.DESTINATION)
                .asId(asId)
                .sessionId(sessionId)
                .svcId("TEST")
                .result(0)
                .wtime("")
                .dummy("")
                .parameters(parameters)
                .build();
    }
    
    /**
     * 질의 요청 메시지 생성
     * 
     * @param phoneNumber 전화번호
     * @param asId AS ID
     * @return 질의 요청 메시지
     */
    public InsupcMessage createQueryRequest(String phoneNumber, int asId) {
        String sessionId = generateSessionId();
        
        List<InsupcMessage.InsupcParameter> parameters = new ArrayList<>();
        
        // Operation Name 파라미터
        parameters.add(InsupcMessage.InsupcParameter.builder()
                .type(InsupcMessage.InsupcParameter.Type.OPERATION_NAME)
                .value("mcidPstnGetInfoV2")
                .build());
        
        // SQL Input 파라미터
        parameters.add(InsupcMessage.InsupcParameter.builder()
                .type(InsupcMessage.InsupcParameter.Type.SQL_INPUT)
                .value(phoneNumber)
                .build());
        
        return InsupcMessage.builder()
                .code(InsupcMessage.Code.QUERY_REQUEST)
                .svca(InsupcMessage.VCA.SOURCE)
                .dvca(InsupcMessage.VCA.DESTINATION)
                .asId(asId)
                .sessionId(sessionId)
                .svcId("TEST")
                .result(0)
                .wtime("")
                .dummy("")
                .parameters(parameters)
                .build();
    }
    
    /**
     * 파라미터 목록 파싱
     */
    private List<InsupcMessage.InsupcParameter> parseParameters(ByteBuffer buffer, int paramCount) {
        List<InsupcMessage.InsupcParameter> parameters = new ArrayList<>();
        
        for (int i = 0; i < paramCount; i++) {
            int type = buffer.getInt();
            
            InsupcMessage.InsupcParameter param = InsupcMessage.InsupcParameter.builder()
                    .type(type)
                    .value(parseParameterValue(buffer, type))
                    .build();
            
            parameters.add(param);
        }
        
        return parameters;
    }
    
    /**
     * 파라미터 값 파싱
     */
    private Object parseParameterValue(ByteBuffer buffer, int type) {
        switch (type) {
            case InsupcMessage.InsupcParameter.Type.OPERATION_NAME:
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
                
            case InsupcMessage.InsupcParameter.Type.LOGON_INFO:
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
     * 파라미터 직렬화
     */
    private void serializeParameter(ByteBuffer buffer, InsupcMessage.InsupcParameter param) {
        buffer.putInt(param.getType());
        
        Object value = param.getValue();
        if (value instanceof String) {
            String str = (String) value;
            byte[] strBytes = str.getBytes(StandardCharsets.UTF_8);
            buffer.putInt(strBytes.length);
            buffer.put(strBytes);
        } else if (value instanceof byte[]) {
            byte[] bytes = (byte[]) value;
            buffer.putInt(bytes.length);
            buffer.put(bytes);
        } else if (value instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> list = (List<String>) value;
            buffer.putInt(list.size());
            for (String item : list) {
                byte[] itemBytes = item.getBytes(StandardCharsets.UTF_8);
                buffer.putInt(itemBytes.length);
                buffer.put(itemBytes);
            }
        }
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