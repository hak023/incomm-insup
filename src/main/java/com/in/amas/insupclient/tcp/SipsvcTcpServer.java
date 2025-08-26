package com.in.amas.insupclient.tcp;

import com.in.amas.insupclient.config.TcpServerConfig;
import com.in.amas.insupclient.dto.SipsvcMessage;
import com.in.amas.insupclient.dto.WorkerMessage;
import com.in.amas.insupclient.protocol.SipsvcProtocolParser;
import com.in.amas.insupclient.service.ConnectionManagementService;
import com.in.amas.insupclient.worker.WorkerThreadPool;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * sipsvc와의 TCP 통신을 담당하는 Netty 기반 TCP 서버
 * JSON/TCP 프로토콜을 사용하여 다중 클라이언트 연결을 관리
 * 
 * @author InComm
 * @version 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SipsvcTcpServer {
    
    private final TcpServerConfig tcpServerConfig;
    private final SipsvcProtocolParser sipsvcProtocolParser;
    private final ConnectionManagementService connectionManagementService;
    private final WorkerThreadPool workerThreadPool;
    
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    
    // 연결된 클라이언트 채널 관리
    private final Map<String, Channel> clientChannels = new ConcurrentHashMap<>();
    
    /**
     * TCP 서버 시작
     */
    @PostConstruct
    public void start() {
        log.info("sipsvc TCP server started - port: {}", tcpServerConfig.getPort());
        
        bossGroup = new NioEventLoopGroup(tcpServerConfig.getBossThreads());
        workerGroup = new NioEventLoopGroup(tcpServerConfig.getWorkerThreads());
        
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, tcpServerConfig.getSoBacklog())
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, tcpServerConfig.isSoKeepalive())
                    .childOption(ChannelOption.TCP_NODELAY, tcpServerConfig.isTcpNodelay())
                    .childOption(ChannelOption.SO_RCVBUF, 32 * 1024)
                    .childOption(ChannelOption.SO_SNDBUF, 32 * 1024)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline pipeline = ch.pipeline();
                            
                            // 유휴 상태 핸들러 (2시간 타임아웃)
                            pipeline.addLast("idleStateHandler", 
                                    new IdleStateHandler(0, 0, 
                                            tcpServerConfig.getConnectionTimeout() / 1000, 
                                            TimeUnit.SECONDS));
                            
                            // 길이 필드 기반 프레임 디코더/인코더 (4바이트 길이 필드)
                            pipeline.addLast("frameDecoder", 
                                    new LengthFieldBasedFrameDecoder(8192, 0, 4, 0, 4));
                            pipeline.addLast("frameEncoder", 
                                    new LengthFieldPrepender(4));
                            
                            // 커스텀 핸들러
                            pipeline.addLast("sipsvcHandler", new SipsvcChannelHandler());
                        }
                    });
            
            // 서버 시작
            ChannelFuture future = bootstrap.bind(tcpServerConfig.getPort()).sync();
            serverChannel = future.channel();
            
            log.info("sipsvc TCP server startup completed - port: {}", tcpServerConfig.getPort());
            
            // 서버 종료 대기 (비동기)
            future.channel().closeFuture().addListener(future1 -> {
                log.info("sipsvc TCP server shutdown");
            });
            
        } catch (Exception e) {
            log.error("sipsvc TCP server startup failed: {}", e.getMessage(), e);
            stop();
        }
    }
    
    /**
     * 클라이언트에게 메시지 전송
     * 
     * @param connectionId 연결 ID
     * @param message 전송할 메시지
     */
    public void sendMessage(String connectionId, SipsvcMessage message) {
        Channel channel = clientChannels.get(connectionId);
        
        if (channel == null || !channel.isActive()) {
            log.warn("Message send failed to inactive connection - connection ID: {}", connectionId);
            return;
        }
        
        try {
            byte[] messageBytes = sipsvcProtocolParser.serializeMessage(message);
            channel.writeAndFlush(io.netty.buffer.Unpooled.wrappedBuffer(messageBytes));
            
            log.debug("sipsvc message sent successfully - connection ID: {}, size: {} bytes", 
                    connectionId, messageBytes.length);
            
        } catch (Exception e) {
            log.error("sipsvc message send failed - connection ID: {}, error: {}", 
                    connectionId, e.getMessage(), e);
        }
    }
    
    /**
     * 클라이언트 연결 종료
     * 
     * @param connectionId 연결 ID
     */
    public void closeConnection(String connectionId) {
        Channel channel = clientChannels.remove(connectionId);
        
        if (channel != null && channel.isActive()) {
            channel.close();
            log.info("Client connection closed - connection ID: {}", connectionId);
        }
    }
    
    /**
     * TCP 서버 종료
     */
    @PreDestroy
    public void stop() {
        log.info("sipsvc TCP server shutdown started");
        
        // 모든 클라이언트 연결 종료
        clientChannels.forEach((id, channel) -> {
            if (channel.isActive()) {
                channel.close();
            }
        });
        clientChannels.clear();
        
        // 서버 채널 종료
        if (serverChannel != null) {
            serverChannel.close();
        }
        
        // EventLoopGroup 종료
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        
        log.info("sipsvc TCP server shutdown completed");
    }
    
    /**
     * 현재 연결된 클라이언트 수 조회
     * 
     * @return 연결된 클라이언트 수
     */
    public int getConnectedClientCount() {
        return (int) clientChannels.values().stream()
                .filter(Channel::isActive)
                .count();
    }
    
    /**
     * sipsvc 클라이언트 처리 핸들러
     */
    private class SipsvcChannelHandler extends ChannelInboundHandlerAdapter {
        
        private String connectionId;
        
        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            InetSocketAddress remoteAddress = (InetSocketAddress) ctx.channel().remoteAddress();
            connectionId = generateConnectionId(remoteAddress);
            
            // 연결 관리 서비스에 등록
            connectionManagementService.registerConnection(
                    connectionId, 
                    remoteAddress.getAddress().getHostAddress(),
                    remoteAddress.getPort()
            );
            
            // 채널 맵에 추가
            clientChannels.put(connectionId, ctx.channel());
            
            log.info("sipsvc client connected - connection ID: {}, IP: {}, Port: {}", 
                    connectionId, 
                    remoteAddress.getAddress().getHostAddress(),
                    remoteAddress.getPort());
            
            super.channelActive(ctx);
        }
        
        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            if (connectionId != null) {
                clientChannels.remove(connectionId);
                connectionManagementService.unregisterConnection(connectionId);
                
                log.info("sipsvc client disconnected - connection ID: {}", connectionId);
            }
            
            super.channelInactive(ctx);
        }
        
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            try {
                io.netty.buffer.ByteBuf byteBuf = (io.netty.buffer.ByteBuf) msg;
                byte[] data = new byte[byteBuf.readableBytes()];
                byteBuf.readBytes(data);
                
                log.debug("sipsvc message received - connection ID: {}, size: {} bytes", 
                        connectionId, data.length);
                
                // JSON 메시지 파싱
                SipsvcMessage sipsvcMessage = sipsvcProtocolParser.parseMessage(data);
                
                // 연결 관리 서비스에 활동 업데이트
                connectionManagementService.incrementClientRequests(connectionId);
                
                // 요청 ID 생성
                String requestId = generateRequestId(sipsvcMessage);
                
                // WorkerMessage 생성 및 큐에 추가
                WorkerMessage workerMessage = WorkerMessage.createSipsvcRequest(
                        connectionId, requestId, sipsvcMessage);
                
                boolean queued = workerThreadPool.submitMessage(workerMessage);
                
                if (!queued) {
                    log.error("WorkerThread queue saturated - message processing failed: connection ID: {}, request ID: {}", 
                            connectionId, requestId);
                    
                    // 오류 응답 전송
                    SipsvcMessage errorResponse = sipsvcProtocolParser.createExecuteResponse(
                            sipsvcMessage, null, 
                            SipsvcMessage.ResultCode.INTERNAL_ERROR, 
                            "Server overloaded");
                    sendMessage(connectionId, errorResponse);
                }
                
            } catch (Exception e) {
                log.error("Error during sipsvc message processing - connection ID: {}, error: {}", 
                        connectionId, e.getMessage(), e);
                
                // 연결 종료
                ctx.close();
            } finally {
                io.netty.util.ReferenceCountUtil.release(msg);
            }
        }
        
        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof io.netty.handler.timeout.IdleStateEvent) {
                log.warn("sipsvc client idle timeout - connection ID: {}", connectionId);
                ctx.close();
            }
            super.userEventTriggered(ctx, evt);
        }
        
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            log.error("sipsvc channel exception occurred - connection ID: {}, error: {}", 
                    connectionId, cause.getMessage(), cause);
            ctx.close();
        }
        
        /**
         * 연결 ID 생성
         */
        private String generateConnectionId(InetSocketAddress remoteAddress) {
            return String.format("sipsvc_%s_%d_%d", 
                    remoteAddress.getAddress().getHostAddress(),
                    remoteAddress.getPort(),
                    System.currentTimeMillis());
        }
        
        /**
         * 요청 ID 생성
         */
        private String generateRequestId(SipsvcMessage message) {
            if (message.getRequestId() != null && !message.getRequestId().trim().isEmpty()) {
                return message.getRequestId();
            }
            
            return String.format("req_%s_%d", 
                    message.getSessionId() != null ? message.getSessionId() : "unknown",
                    System.currentTimeMillis());
        }
    }
}
