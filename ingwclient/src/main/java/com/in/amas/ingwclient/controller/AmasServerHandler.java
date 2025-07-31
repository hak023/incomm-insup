package com.in.amas.ingwclient.controller;

import com.in.amas.ingwclient.model.server.AuthRequestDTO;
import com.in.amas.ingwclient.model.server.BaseRequest;
import com.in.amas.ingwclient.model.server.ExecuteRequestDTO;
import com.in.amas.ingwclient.model.server.HeartbeatRequestDTO;
import com.in.amas.ingwclient.service.AmasService;
import com.in.amas.ingwclient.util.AmasMessageParser;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.springframework.stereotype.Component;

@Component
@ChannelHandler.Sharable
public class AmasServerHandler extends SimpleChannelInboundHandler<String> {
    private final AmasService amasService;

    public AmasServerHandler(AmasService amasService) {
        this.amasService = amasService;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
        String body = AmasMessageParser.extractBody(msg);
        BaseRequest req = AmasMessageParser.parse(body);

        String responseJson;

        switch (req.getCmd()) {
            case "auth":
                responseJson = amasService.authenticate((AuthRequestDTO) req);
                break;
            case "heartBeat":
                responseJson = amasService.checkSession((HeartbeatRequestDTO) req);
                break;
            case "execute":
                responseJson = amasService.executeService((ExecuteRequestDTO) req);
                break;
            default:
                responseJson = AmasMessageParser.wrapResponse(
                        "{\"result\":-100,\"resultDesc\":\"Unknown cmd\"}");
        }

        ctx.writeAndFlush(responseJson);
    }
}