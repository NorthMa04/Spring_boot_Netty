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
                // 60s 无读算一次空闲，5 次后（5×60s = 300s）才触发休眠
                .addLast(new CustomIdleStateHandler(
                        60, 0, 0, TimeUnit.SECONDS,
                        5     // skipCount 从 10 改为 5
                ))
                // 休眠 & 唤醒逻辑
                .addLast(new SleepStateHandler())
                // 原有 String 编解码与业务处理
                .addLast(new StringDecoder(StandardCharsets.UTF_8))
                .addLast(new StringEncoder(StandardCharsets.UTF_8))
                .addLast(serverHandler);
    }
}
