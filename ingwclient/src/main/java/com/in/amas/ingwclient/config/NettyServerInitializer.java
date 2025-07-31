package com.in.amas.ingwclient.config;

import com.in.amas.ingwclient.controller.AmasServerHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class NettyServerInitializer extends ChannelInitializer<SocketChannel> {
    private final AmasServerHandler amasServerHandler;

    @Autowired
    public NettyServerInitializer(AmasServerHandler amasServerHandler) {
        this.amasServerHandler = amasServerHandler;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        ch.pipeline()
                .addLast(new StringLengthHeaderDecoder())
                .addLast(new StringDecoder(StandardCharsets.UTF_8))
                .addLast(new StringEncoder(StandardCharsets.UTF_8))
                .addLast(amasServerHandler);
    }

    private static class StringLengthHeaderDecoder extends ByteToMessageDecoder {
        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
            if (in.readableBytes() < 10) return;

            in.markReaderIndex();
            byte start = in.readByte(); // '('
            byte[] lenBytes = new byte[8];
            in.readBytes(lenBytes);
            byte end = in.readByte(); // ')'

            String lenStr = new String(lenBytes, StandardCharsets.UTF_8);
            int bodyLength = Integer.parseInt(lenStr);

            if (in.readableBytes() < bodyLength) {
                in.resetReaderIndex();
                return;
            }

            byte[] body = new byte[bodyLength];
            in.readBytes(body);

            out.add(new String(body, StandardCharsets.UTF_8));
        }
    }
}
