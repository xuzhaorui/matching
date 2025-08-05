package com.match;



import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;


@Component
@ChannelHandler.Sharable
@RequiredArgsConstructor
public class WebSocketHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private static final Logger log = LoggerFactory.getLogger(WebSocketHandler.class);

    private final ShardedChannelRegistry shardedChannelRegistry;
    private final EnhancedMatchEngine enhancedMatchEngine;




    @Override
    public void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame msg) throws Exception {
        String json = msg.text();
        Player player = JsonUtil.jsonToBo(Player.class, json);
        if (player != null) {
            if (!enhancedMatchEngine.submitEvent(player.getUsername(), player.getScore(), player.getMatchRange(), ctx.channel().id().asLongText())) {
                System.out.println(" 检测 Heap 使用率 达到后压阈值，上游退避");
            }

        }

    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        shardedChannelRegistry.register(ctx);
    }
    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        shardedChannelRegistry.unregister(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        shardedChannelRegistry.unregister(ctx);
        super.channelInactive(ctx);
    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)  {
        log.error("发生异常: ", cause);
        ctx.close();
    }


}
