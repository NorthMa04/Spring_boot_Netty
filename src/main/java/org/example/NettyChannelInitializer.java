package org.example;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.json.JsonObjectDecoder;
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

    @Autowired
    private SleepStateHandler sleepStateHandler;

    @Override
    protected void initChannel(SocketChannel ch) {
        ch.pipeline()
                // JSON 拆帧
                .addLast(new JsonObjectDecoder())
                // 编码/解码
                .addLast(new StringDecoder(StandardCharsets.UTF_8))
                .addLast(new StringEncoder(StandardCharsets.UTF_8))
                // 空闲检测：读空闲 60s，skip 5 次再触发
                .addLast(new CustomIdleStateHandler(
                        60, 0, 0, TimeUnit.SECONDS, 3
                ))
                // 休眠/唤醒逻辑（改为使用 ChannelManager）
                .addLast(sleepStateHandler)
                // 核心业务
                .addLast(serverHandler);
    }
}
