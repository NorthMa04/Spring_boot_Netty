package org.example;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Component
public class NettyChannelInitializer extends ChannelInitializer<SocketChannel> {

    @Autowired
    private ServerHandler serverHandler;

    @Override
    protected void initChannel(SocketChannel ch) {
        ch.pipeline()
                // 1. 连续 60s 无读算一次空闲，10 次后（10 分钟）触发一次
                .addLast(new CustomIdleStateHandler(
                        60, 0, 0, TimeUnit.SECONDS,
                        10
                ))
                // 2. 收到“生效”的空闲事件后进入休眠，并启动 30s 唤醒调度
                .addLast(new SleepStateHandler())
                // 3. 原有的业务编解码与 ServerHandler
                .addLast(new StringDecoder(StandardCharsets.UTF_8))
                .addLast(new StringEncoder(StandardCharsets.UTF_8))
                .addLast(serverHandler);
    }
}
