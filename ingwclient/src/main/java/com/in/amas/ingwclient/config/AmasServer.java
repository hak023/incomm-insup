package com.in.amas.ingwclient.config;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AmasServer {
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private ChannelFuture serverChannelFuture;

    private final NettyServerInitializer nettyServerInitializer;
    private final ApplicationProperties prop;

    public AmasServer(NettyServerInitializer nettyServerInitializer, ApplicationProperties applicationProperties) {
        this.nettyServerInitializer = nettyServerInitializer;
        this.prop = applicationProperties;
    }

    @PostConstruct
    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        Thread nettyThread = new Thread(() -> {
            try {
                ServerBootstrap bootstrap = new ServerBootstrap();
                bootstrap.group(bossGroup, workerGroup)
                        .channel(NioServerSocketChannel.class)
                        .childHandler(nettyServerInitializer);
                final int port = prop.getServer().getPort();
                serverChannelFuture = bootstrap.bind(port).sync();
                log.info("AMAS Server started on port " + port);
                serverChannelFuture.channel().closeFuture().sync();
            } catch (InterruptedException e) {
                log.error("AMAS Server interrupted", e);
                Thread.currentThread().interrupt();
            } finally {
                shutdown();
            }
        });
        nettyThread.setDaemon(true);
        nettyThread.start();
    }

    @PreDestroy
    public void shutdown() {
        if (bossGroup != null) bossGroup.shutdownGracefully();
        if (workerGroup != null) workerGroup.shutdownGracefully();
    }
}
