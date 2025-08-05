package com.match;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;


/**
 * 服务器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketServer {
    private final WebSocketHandler webSocketHandler;

    @PostConstruct
        // 使用CompletableFuture异步执行
    public void start() {
        CompletableFuture.runAsync(() -> {
            // 只用一个 boss 线程，worker 线程数 = CPU 核心数
            EventLoopGroup bossGroup   = new NioEventLoopGroup(3);
            EventLoopGroup workerGroup = new NioEventLoopGroup(
                    Runtime.getRuntime().availableProcessors()
            );

            // 使用无池化的 ByteBufAllocator
            UnpooledByteBufAllocator alloc = UnpooledByteBufAllocator.DEFAULT;

                // 创建ServerBootstrap实例
            try {
                // 设置bossGroup和workerGroup
                ServerBootstrap b = new ServerBootstrap();
                        // 设置通道类型为NioServerSocketChannel
                b.group(bossGroup, workerGroup)
                        // 设置ByteBufAllocator
                        .channel(NioServerSocketChannel.class)
                        .option(ChannelOption.ALLOCATOR, alloc)
                        // 设置ChannelInitializer
                        .childOption(ChannelOption.ALLOCATOR, alloc)
                        .childHandler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) {
                                // 添加HttpServerCodec
                                ChannelPipeline p = ch.pipeline();
                                // 添加HttpObjectAggregator
                                p.addLast(new HttpServerCodec());
                                // 添加WebSocketServerProtocolHandler
                                p.addLast(new HttpObjectAggregator(65536));
                                // 添加DefaultEventExecutorGroup
                                p.addLast(new WebSocketServerProtocolHandler("/ws"));
                                // 添加WebSocketHandler
                                p.addLast(new DefaultEventExecutorGroup(10));
                                p.addLast(webSocketHandler);
                            }
                // 绑定端口并启动服务
                        });
                b.bind(8889).sync().channel().closeFuture().sync();
                // 捕获InterruptedException并中断当前线程
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // 关闭bossGroup和workerGroup
            } finally {
                bossGroup.shutdownGracefully();
                workerGroup.shutdownGracefully();
            }
        });
    }

}
