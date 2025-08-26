package com.in.amas.insupclient.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.in.amas.insupclient.dto.SipsvcMessage;
import com.in.amas.insupclient.dto.InsupcMessage;
import com.in.amas.insupclient.protocol.InsupcProtocolParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Incomm-Insup Gateway 테스트 시뮬레이터
 * - INSUPC 서버 역할 (TCP Server)
 * - sipsvc 클라이언트 역할 (TCP Client)
 * 
 * @author InComm
 * @version 1.0.0
 */
@Slf4j
@SpringBootApplication
public class TestSimulator {
    
    private static final String GATEWAY_HOST = "127.0.0.1";
    private static final int GATEWAY_PORT = 9090;  // sipsvc → Gateway
    private static final int INSUPC_PORT = 19000;  // Gateway → INSUPC (시뮬레이터)
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final InsupcProtocolParser insupcProtocolParser = new InsupcProtocolParser();
    
    private ServerSocket insupcServerSocket;
    private volatile boolean running = true;

    public static void main(String[] args) {
        SpringApplication.run(TestSimulator.class, args);
    }
    
    @Bean
    public CommandLineRunner run() {
        return args -> {
            log.info("=".repeat(60));
            log.info("Incomm-Insup Gateway test simulator started");
            log.info("=".repeat(60));
            
            // 1. INSUPC 서버 시작 (Gateway가 연결할 서버)
            startInsupcServer();
            
            // 2. 잠시 대기 후 sipsvc 클라이언트 테스트 시작
            Thread.sleep(2000);
            startSipsvcClient();
            
            // 3. 테스트 완료 대기
            Thread.sleep(10000);
            
            log.info("Test completed - simulator shutdown");
            running = false;
            if (insupcServerSocket != null && !insupcServerSocket.isClosed()) {
                insupcServerSocket.close();
            }
        };
    }
    
    /**
     * INSUPC 서버 시작 (Gateway가 연결할 대상)
     */
    private void startInsupcServer() {
        CompletableFuture.runAsync(() -> {
            try {
                insupcServerSocket = new ServerSocket(INSUPC_PORT);
                log.info("🟢 INSUPC simulator server started - port: {}", INSUPC_PORT);
                
                while (running && !insupcServerSocket.isClosed()) {
                    try {
                        Socket clientSocket = insupcServerSocket.accept();
                        log.info("🔗 INSUPC connection accepted from Gateway - {}", clientSocket.getRemoteSocketAddress());
                        
                        // 각 연결을 별도 스레드에서 처리
                        CompletableFuture.runAsync(() -> handleInsupcClient(clientSocket));
                        
                    } catch (IOException e) {
                        if (running) {
                            log.error("INSUPC server connection accept failed: {}", e.getMessage());
                        }
                    }
                }
                
            } catch (IOException e) {
                log.error("INSUPC server start failed: {}", e.getMessage(), e);
            }
        });
    }
    
    /**
     * INSUPC 클라이언트 연결 처리
     */
    private void handleInsupcClient(Socket clientSocket) {
        try (DataInputStream dis = new DataInputStream(clientSocket.getInputStream());
             DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream())) {
            
            log.info("📨 INSUPC client processing started - {}", clientSocket.getRemoteSocketAddress());
            
            while (running && !clientSocket.isClosed()) {
                try {
                    // 메시지 길이 읽기 (4바이트)
                    int messageLength = dis.readInt();
                    
                    if (messageLength <= 0 || messageLength > 8192) {
                        log.warn("Abnormal message length: {}", messageLength);
                        break;
                    }
                    
                    // 메시지 데이터 읽기
                    byte[] messageData = new byte[messageLength];
                    dis.readFully(messageData);
                    
                    log.info("📥 INSUPC message received - size: {} bytes", messageLength);
                    
                    // 메시지 파싱 및 응답 처리
                    handleInsupcMessage(messageData, dos);
                    
                } catch (EOFException e) {
                    log.info("Client connection terminated");
                    break;
                } catch (IOException e) {
                    log.error("Error during INSUPC message processing: {}", e.getMessage());
                    break;
                }
            }
            
        } catch (IOException e) {
            log.error("INSUPC client processing failed: {}", e.getMessage(), e);
        } finally {
            try {
                clientSocket.close();
                log.info("🔌 INSUPC client connection closed");
            } catch (IOException e) {
                log.warn("Socket close failed: {}", e.getMessage());
            }
        }
    }
    
    /**
     * INSUPC 메시지 처리 및 응답
     */
    private void handleInsupcMessage(byte[] messageData, DataOutputStream dos) throws IOException {
        try {
            // 메시지 파싱
            InsupcMessage request = insupcProtocolParser.parseMessage(messageData);
            
            log.info("📋 INSUPC request processing - code: {}, session: {}", 
                    request.getMsgCode(), request.getSessionId());
            
            InsupcMessage response = null;
            
            switch (request.getMsgCode()) {
                case InsupcMessage.MessageCode.DB_ACCESS_REQUEST:
                    response = createAccessResponse(request);
                    break;
                    
                case InsupcMessage.MessageCode.DB_QUERY_REQUEST:
                    response = createQueryResponse(request);
                    break;
                    
                default:
                    log.warn("Unknown INSUPC request code: {}", request.getMsgCode());
                    return;
            }
            
            if (response != null) {
                // 응답 메시지 직렬화 및 전송
                byte[] responseData = insupcProtocolParser.serializeMessage(response);
                
                dos.writeInt(responseData.length);
                dos.write(responseData);
                dos.flush();
                
                log.info("📤 INSUPC response sent successfully - code: {}, size: {} bytes", 
                        response.getMsgCode(), responseData.length);
            }
            
        } catch (Exception e) {
            log.error("INSUPC message processing failed: {}", e.getMessage(), e);
        }
    }
    
    /**
     * DB 접근 응답 생성 (C++ 구현과 동일)
     */
    private InsupcMessage createAccessResponse(InsupcMessage request) {
        return InsupcMessage.builder()
                .msgCode(InsupcMessage.MessageCode.DB_ACCESS_RESPONSE)
                .svca(request.getDvca())
                .dvca(request.getSvca())
                .inasId(request.getInasId())
                .sessionId(request.getSessionId())
                .svcId(request.getSvcId())
                .result(InsupcMessage.ResultCode.SUCCESS)
                .wtime(getCurrentTimeString())
                .majorVersion(1)
                .minorVersion(0)
                .dummy(0)
                .useRequestAck(InsupcMessage.RequestAck.DONT_USE_REQUEST_ACK)
                .parameters(java.util.List.of(
                        InsupcMessage.InsupcParameter.builder()
                                .type(InsupcMessage.InsupcParameter.Type.DB_LOGON_INFO)
                                .size(8)
                                .value(new byte[]{(byte)0xF0, 0x01, 0x01, 0x03, 127, 0, 0, 1})
                                .build(),
                        InsupcMessage.InsupcParameter.builder()
                                .type(InsupcMessage.InsupcParameter.Type.DB_STATUS)
                                .size(2)
                                .value("MA")
                                .build()
                ))
                .build();
    }
    
    /**
     * 질의 응답 생성 (가입자 정보)
     */
    private InsupcMessage createQueryResponse(InsupcMessage request) {
        // 요청에서 전화번호 추출
        String phoneNumber = "025671033";  // 기본값
        
        if (request.getParameters() != null) {
            for (InsupcMessage.InsupcParameter param : request.getParameters()) {
                if (param.getType() == InsupcMessage.InsupcParameter.Type.SQL_INPUT) {
                    phoneNumber = param.getValue().toString();
                    break;
                }
            }
        }
        
        // 가입자 정보 응답 생성 (로그 예제 기반)
        java.util.List<String> sqlOutput = java.util.List.of(
                "1",          // 1) = '1':1
                phoneNumber,  // 2) = '025671033':9  
                "10",         // 3) = '10':2
                "0",          // 4) = '0':1
                "1",          // 5) = '1':1
                "50500",      // 6) = '50500':5
                "1",          // 7) = '1':1
                "10100",      // 8) = '10100':5
                phoneNumber   // 9) = '025671033':9
        );
        
        return InsupcMessage.builder()
                .msgCode(InsupcMessage.MessageCode.DB_QUERY_RESPONSE)
                .svca(request.getDvca())
                .dvca(request.getSvca())
                .inasId(request.getInasId())
                .sessionId(request.getSessionId())
                .svcId(request.getSvcId())
                .result(InsupcMessage.ResultCode.SUCCESS)
                .wtime(getCurrentTimeString())
                .majorVersion(1)
                .minorVersion(0)
                .dummy(0)
                .useRequestAck(InsupcMessage.RequestAck.DONT_USE_REQUEST_ACK)
                .parameters(java.util.List.of(
                        InsupcMessage.InsupcParameter.builder()
                                .type(InsupcMessage.InsupcParameter.Type.DB_OPERATION_NAME)
                                .size("mcidPstnGetInfoV2".length() + 1)
                                .value("mcidPstnGetInfoV2")
                                .build(),
                        InsupcMessage.InsupcParameter.builder()
                                .type(InsupcMessage.InsupcParameter.Type.SQL_OUTPUT)
                                .size(calculateSqlOutputSize(sqlOutput))
                                .value(sqlOutput)
                                .build(),
                        InsupcMessage.InsupcParameter.builder()
                                .type(InsupcMessage.InsupcParameter.Type.SQL_RESULT)
                                .size(2)  // result_category(1) + result_value(1)
                                .value(new byte[]{
                                    (byte)InsupcMessage.InsupcParameter.SqlResultCategory.SUCCESS,
                                    (byte)InsupcMessage.InsupcParameter.SqlResultValue.SUCCESS
                                })
                                .build()
                ))
                .build();
    }
    
    /**
     * SQL Output 파라미터 크기 계산
     */
    private int calculateSqlOutputSize(java.util.List<String> sqlOutput) {
        int size = 1; // 필드 개수 (1바이트)
        for (String field : sqlOutput) {
            size += 2; // 길이 필드 (2바이트)
            size += field.getBytes(java.nio.charset.StandardCharsets.UTF_8).length; // 값
        }
        return size;
    }
    
    /**
     * sipsvc 클라이언트 테스트 시작
     */
    private void startSipsvcClient() {
        CompletableFuture.runAsync(() -> {
            try {
                log.info("🚀 sipsvc client test started");
                
                // Gateway에 연결
                try (Socket socket = new Socket(GATEWAY_HOST, GATEWAY_PORT);
                     DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                     DataInputStream dis = new DataInputStream(socket.getInputStream())) {
                    
                    log.info("🔗 Gateway connection successful - {}:{}", GATEWAY_HOST, GATEWAY_PORT);
                    
                    // 1. 인증 메시지 전송
                    sendAuthMessage(dos);
                    readResponse(dis, "Authentication");
                    
                    Thread.sleep(1000);
                    
                    // 2. Heartbeat 메시지 전송
                    sendHeartbeatMessage(dos);
                    readResponse(dis, "Heartbeat");
                    
                    Thread.sleep(1000);
                    
                    // 3. Execute 메시지 전송 (가입자 정보 조회)
                    sendExecuteMessage(dos);
                    readResponse(dis, "Execute");
                    
                    log.info("✅ sipsvc client test completed");
                    
                } catch (IOException e) {
                    log.error("sipsvc client test failed: {}", e.getMessage(), e);
                }
                
            } catch (Exception e) {
                log.error("Error during sipsvc client execution: {}", e.getMessage(), e);
            }
        });
    }
    
    /**
     * 인증 메시지 전송
     */
    private void sendAuthMessage(DataOutputStream dos) throws IOException {
        SipsvcMessage authMessage = SipsvcMessage.builder()
                .type(SipsvcMessage.Type.AUTH)
                .sessionId("test_session_001")
                .clientIp("127.0.0.1")
                .macAddress("00:00:00:00:00:01")
                .authKey("TEST_AUTH_KEY_001")
                .timestamp(System.currentTimeMillis())
                .build();
        
        sendJsonMessage(dos, authMessage, "Authentication");
    }
    
    /**
     * Heartbeat 메시지 전송
     */
    private void sendHeartbeatMessage(DataOutputStream dos) throws IOException {
        SipsvcMessage heartbeatMessage = SipsvcMessage.builder()
                .type(SipsvcMessage.Type.HEARTBEAT)
                .sessionId("test_session_001")
                .clientIp("127.0.0.1")
                .timestamp(System.currentTimeMillis())
                .build();
        
        sendJsonMessage(dos, heartbeatMessage, "Heartbeat");
    }
    
    /**
     * Execute 메시지 전송
     */
    private void sendExecuteMessage(DataOutputStream dos) throws IOException {
        SipsvcMessage executeMessage = SipsvcMessage.builder()
                .type(SipsvcMessage.Type.EXECUTE)
                .sessionId("test_session_001")
                .requestId("test_req_001")
                .clientIp("127.0.0.1")
                .phoneNumber("025671033")
                .serviceCode("mcidPstnGetInfoV2")
                .timestamp(System.currentTimeMillis())
                .build();
        
        sendJsonMessage(dos, executeMessage, "Execute");
    }
    
    /**
     * JSON 메시지 전송
     */
    private void sendJsonMessage(DataOutputStream dos, SipsvcMessage message, String messageType) throws IOException {
        try {
            String jsonString = objectMapper.writeValueAsString(message);
            byte[] jsonBytes = jsonString.getBytes(StandardCharsets.UTF_8);
            
            // 길이 필드(4바이트) + JSON 데이터 전송
            dos.writeInt(jsonBytes.length);
            dos.write(jsonBytes);
            dos.flush();
            
            log.info("📤 {} message sent - size: {} bytes", messageType, jsonBytes.length);
            log.debug("📤 {} message content: {}", messageType, jsonString);
            
        } catch (Exception e) {
            log.error("{} message send failed: {}", messageType, e.getMessage(), e);
            throw new IOException("Message send failed", e);
        }
    }
    
    /**
     * 응답 메시지 읽기
     */
    private void readResponse(DataInputStream dis, String messageType) {
        try {
            // 타임아웃 설정 (5초)
            dis.available(); // 연결 확인
            
            // 길이 필드 읽기
            int responseLength = dis.readInt();
            
            if (responseLength <= 0 || responseLength > 8192) {
                log.warn("{} response length abnormal: {}", messageType, responseLength);
                return;
            }
            
            // 응답 데이터 읽기
            byte[] responseData = new byte[responseLength];
            dis.readFully(responseData);
            
            String responseJson = new String(responseData, StandardCharsets.UTF_8);
            SipsvcMessage response = objectMapper.readValue(responseJson, SipsvcMessage.class);
            
            log.info("📥 {} response received - result: {}, message: {}", 
                    messageType, response.getResultCode(), response.getResultMessage());
            log.debug("📥 {} response content: {}", messageType, responseJson);
            
        } catch (Exception e) {
            log.error("{} response read failed: {}", messageType, e.getMessage(), e);
        }
    }
    
    /**
     * 현재 시간 문자열 생성 (INSUPC 형식)
     */
    private String getCurrentTimeString() {
        return java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
    }
}
