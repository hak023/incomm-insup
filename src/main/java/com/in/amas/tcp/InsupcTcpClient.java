package com.in.amas.tcp;

import com.in.amas.config.InsupcConfig;
import com.in.amas.dto.InsupcMessage;
import com.in.amas.dto.WorkerMessage;
import com.in.amas.protocol.InsupcProtocolParser;
import com.in.amas.worker.WorkerThreadPool;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.timeout.ReadTimeoutHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * INSUPC와의 TCP 통신을 담당하는 Netty 기반 TCP 클라이언트
 * Binary/TCP 프로토콜을 사용하여 다중 INSUPC 서버와 연결 풀 관리
 * 
 * @author InComm
 * @version 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InsupcTcpClient {
    
    private final InsupcConfig insupcConfig;
    private final InsupcProtocolParser insupcProtocolParser;
    private final WorkerThreadPool workerThreadPool;
    
    private EventLoopGroup workerGroup;
    private final Map<String, InsupcConnectionPool> connectionPools = new ConcurrentHashMap<>();
    private final Map<String, String> requestToConnectionMap = new ConcurrentHashMap<>();
    
    private ScheduledExecutorService reconnectScheduler;
    
    /**
     * INSUPC 클라이언트 시작
     */
    @PostConstruct
    public void start() {
        log.info("INSUPC TCP 클라이언트 시작");
        
        workerGroup = new NioEventLoopGroup();
        reconnectScheduler = Executors.newScheduledThreadPool(2);
        
        // 각 INSUPC 서버에 대한 연결 풀 생성
        List<InsupcConfig.InsupcClient> clients = insupcConfig.getClients();
        if (clients != null) {
            for (InsupcConfig.InsupcClient clientConfig : clients) {
                InsupcConnectionPool pool = new InsupcConnectionPool(clientConfig);
                connectionPools.put(clientConfig.getName(), pool);
                pool.initialize();
                
                log.info("INSUPC 연결 풀 생성 - 이름: {}, 호스트: {}:{}, 풀 크기: {}", 
                        clientConfig.getName(), clientConfig.getHost(), 
                        clientConfig.getPort(), clientConfig.getConnectionPoolSize());
            }
        }
        
        log.info("INSUPC TCP 클라이언트 시작 완료 - {} 개 연결 풀", connectionPools.size());
    }
    
    /**
     * INSUPC로 메시지 전송
     * 
     * @param message 전송할 메시지
     * @param requestId 요청 ID
     * @return 전송 성공 여부
     */
    public boolean sendMessage(InsupcMessage message, String requestId) {
        // Round-Robin 방식으로 연결 풀 선택
        InsupcConnectionPool selectedPool = selectConnectionPool();
        
        if (selectedPool == null) {
            log.error("사용 가능한 INSUPC 연결 풀이 없음 - 요청 ID: {}", requestId);
            return false;
        }
        
        return selectedPool.sendMessage(message, requestId);
    }
    
    /**
     * INSUPC 클라이언트 종료
     */
    @PreDestroy
    public void stop() {
        log.info("INSUPC TCP 클라이언트 종료 시작");
        
        // 연결 풀 종료
        connectionPools.values().forEach(InsupcConnectionPool::shutdown);
        connectionPools.clear();
        
        // 스케줄러 종료
        if (reconnectScheduler != null && !reconnectScheduler.isShutdown()) {
            reconnectScheduler.shutdown();
        }
        
        // EventLoopGroup 종료
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        
        log.info("INSUPC TCP 클라이언트 종료 완료");
    }
    
    /**
     * Round-Robin 방식으로 연결 풀 선택
     */
    private final AtomicInteger poolIndex = new AtomicInteger(0);
    
    private InsupcConnectionPool selectConnectionPool() {
        List<InsupcConnectionPool> availablePools = connectionPools.values().stream()
                .filter(pool -> pool.hasAvailableConnection())
                .toList();
        
        if (availablePools.isEmpty()) {
            return null;
        }
        
        int index = poolIndex.getAndIncrement() % availablePools.size();
        return availablePools.get(index);
    }
    
    /**
     * INSUPC 연결 풀 클래스
     */
    private class InsupcConnectionPool {
        
        private final InsupcConfig.InsupcClient config;
        private final BlockingQueue<Channel> availableConnections;
        private final Map<String, Channel> requestChannelMap = new ConcurrentHashMap<>();
        private volatile boolean initialized = false;
        private volatile boolean shutdown = false;
        
        public InsupcConnectionPool(InsupcConfig.InsupcClient config) {
            this.config = config;
            this.availableConnections = new LinkedBlockingQueue<>();
        }
        
        /**
         * 연결 풀 초기화
         */
        public void initialize() {
            log.info("INSUPC 연결 풀 초기화 시작 - {}", config.getName());
            
            for (int i = 0; i < config.getConnectionPoolSize(); i++) {
                createConnection();
            }
            
            initialized = true;
            log.info("INSUPC 연결 풀 초기화 완료 - {}, 연결 수: {}", 
                    config.getName(), availableConnections.size());
        }
        
        /**
         * 새로운 연결 생성
         */
        private void createConnection() {
            try {
                Bootstrap bootstrap = new Bootstrap();
                bootstrap.group(workerGroup)
                        .channel(NioSocketChannel.class)
                        .option(ChannelOption.SO_KEEPALIVE, true)
                        .option(ChannelOption.TCP_NODELAY, true)
                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, config.getConnectionTimeout())
                        .handler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) throws Exception {
                                ChannelPipeline pipeline = ch.pipeline();
                                
                                // 읽기 타임아웃
                                pipeline.addLast("readTimeoutHandler", 
                                        new ReadTimeoutHandler(config.getReadTimeout(), TimeUnit.MILLISECONDS));
                                
                                // 길이 필드 기반 프레임 디코더/인코더 (4바이트 길이 필드)
                                pipeline.addLast("frameDecoder", 
                                        new LengthFieldBasedFrameDecoder(8192, 0, 4, 0, 0));
                                pipeline.addLast("frameEncoder", 
                                        new LengthFieldPrepender(4));
                                
                                // 커스텀 핸들러
                                pipeline.addLast("insupcHandler", new InsupcChannelHandler());
                            }
                        });
                
                ChannelFuture future = bootstrap.connect(config.getHost(), config.getPort()).sync();
                Channel channel = future.channel();
                
                // 연결 성공 시 로그온 요청 전송
                InsupcMessage logonRequest = insupcProtocolParser.createLogonRequest(1);
                sendLogonRequest(channel, logonRequest);
                
                // 사용 가능한 연결로 추가
                availableConnections.offer(channel);
                
                log.debug("INSUPC 연결 생성 완료 - {}, 채널: {}", config.getName(), channel.id());
                
            } catch (Exception e) {
                log.error("INSUPC 연결 생성 실패 - {}, 오류: {}", config.getName(), e.getMessage(), e);
                
                // 재연결 스케줄링
                if (!shutdown) {
                    scheduleReconnect();
                }
            }
        }
        
        /**
         * 로그온 요청 전송
         */
        private void sendLogonRequest(Channel channel, InsupcMessage logonRequest) {
            try {
                byte[] messageBytes = insupcProtocolParser.serializeMessage(logonRequest);
                channel.writeAndFlush(io.netty.buffer.Unpooled.wrappedBuffer(messageBytes));
                
                log.debug("INSUPC 로그온 요청 전송 - {}, 세션: {}", 
                        config.getName(), logonRequest.getSessionId());
                
            } catch (Exception e) {
                log.error("INSUPC 로그온 요청 전송 실패 - {}, 오류: {}", 
                        config.getName(), e.getMessage(), e);
            }
        }
        
        /**
         * 메시지 전송
         */
        public boolean sendMessage(InsupcMessage message, String requestId) {
            Channel channel = null;
            
            try {
                // 사용 가능한 연결 획득
                channel = availableConnections.poll(1, TimeUnit.SECONDS);
                
                if (channel == null || !channel.isActive()) {
                    log.warn("사용 가능한 INSUPC 연결이 없음 - {}, 요청 ID: {}", 
                            config.getName(), requestId);
                    return false;
                }
                
                // 요청-채널 매핑 저장
                requestChannelMap.put(requestId, channel);
                
                // 메시지 전송
                byte[] messageBytes = insupcProtocolParser.serializeMessage(message);
                channel.writeAndFlush(io.netty.buffer.Unpooled.wrappedBuffer(messageBytes));
                
                log.info("INSUPC 메시지 전송 완료 - {}, 요청 ID: {}, 코드: {}", 
                        config.getName(), requestId, message.getCode());
                
                return true;
                
            } catch (Exception e) {
                log.error("INSUPC 메시지 전송 실패 - {}, 요청 ID: {}, 오류: {}", 
                        config.getName(), requestId, e.getMessage(), e);
                
                // 실패한 연결은 다시 풀에 반환하지 않음
                if (channel != null) {
                    channel.close();
                }
                
                return false;
            }
        }
        
        /**
         * 응답 처리 후 연결 반환
         */
        public void returnConnection(String requestId, Channel channel) {
            requestChannelMap.remove(requestId);
            
            if (channel.isActive() && !shutdown) {
                availableConnections.offer(channel);
            }
        }
        
        /**
         * 사용 가능한 연결이 있는지 확인
         */
        public boolean hasAvailableConnection() {
            return initialized && !shutdown && !availableConnections.isEmpty();
        }
        
        /**
         * 재연결 스케줄링
         */
        private void scheduleReconnect() {
            reconnectScheduler.schedule(() -> {
                if (!shutdown) {
                    createConnection();
                }
            }, config.getRetryInterval(), TimeUnit.MILLISECONDS);
        }
        
        /**
         * 연결 풀 종료
         */
        public void shutdown() {
            shutdown = true;
            
            // 모든 연결 종료
            availableConnections.forEach(channel -> {
                if (channel.isActive()) {
                    channel.close();
                }
            });
            availableConnections.clear();
            
            requestChannelMap.values().forEach(channel -> {
                if (channel.isActive()) {
                    channel.close();
                }
            });
            requestChannelMap.clear();
            
            log.info("INSUPC 연결 풀 종료 완료 - {}", config.getName());
        }
    }
    
    /**
     * INSUPC 서버 응답 처리 핸들러
     */
    private class InsupcChannelHandler extends ChannelInboundHandlerAdapter {
        
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            try {
                io.netty.buffer.ByteBuf byteBuf = (io.netty.buffer.ByteBuf) msg;
                byte[] data = new byte[byteBuf.readableBytes()];
                byteBuf.readBytes(data);
                
                log.debug("INSUPC 응답 수신 - 채널: {}, 크기: {} bytes", 
                        ctx.channel().id(), data.length);
                
                // 바이너리 메시지 파싱
                InsupcMessage insupcMessage = insupcProtocolParser.parseMessage(data);
                
                // 세션 ID를 기반으로 요청 ID 찾기
                String requestId = findRequestIdBySessionId(insupcMessage.getSessionId());
                
                if (requestId != null) {
                    // WorkerMessage 생성 및 큐에 추가
                    WorkerMessage workerMessage = WorkerMessage.createInsupcResponse(requestId, insupcMessage);
                    workerThreadPool.submitMessage(workerMessage);
                    
                    // 연결 반환
                    returnConnectionByRequestId(requestId, ctx.channel());
                    
                    log.info("INSUPC 응답 처리 완료 - 요청 ID: {}, 코드: {}", 
                            requestId, insupcMessage.getCode());
                } else {
                    log.warn("INSUPC 응답에 대응하는 요청을 찾을 수 없음 - 세션 ID: {}", 
                            insupcMessage.getSessionId());
                }
                
            } catch (Exception e) {
                log.error("INSUPC 응답 처리 중 오류 - 채널: {}, 오류: {}", 
                        ctx.channel().id(), e.getMessage(), e);
            } finally {
                io.netty.util.ReferenceCountUtil.release(msg);
            }
        }
        
        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            log.warn("INSUPC 연결 해제됨 - 채널: {}", ctx.channel().id());
            super.channelInactive(ctx);
        }
        
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            log.error("INSUPC 채널 예외 발생 - 채널: {}, 오류: {}", 
                    ctx.channel().id(), cause.getMessage(), cause);
            ctx.close();
        }
    }
    
    /**
     * 세션 ID로 요청 ID 찾기
     */
    private String findRequestIdBySessionId(String sessionId) {
        return requestToConnectionMap.entrySet().stream()
                .filter(entry -> entry.getValue().equals(sessionId))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }
    
    /**
     * 요청 ID로 연결 반환
     */
    private void returnConnectionByRequestId(String requestId, Channel channel) {
        connectionPools.values().forEach(pool -> pool.returnConnection(requestId, channel));
    }
}