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
    private SleepStateHandler sleepStateHandler;  // 一定要注入

    @Override
    protected void initChannel(SocketChannel ch) {
        ch.pipeline()
                .addLast(new JsonObjectDecoder())
                .addLast(new StringDecoder(StandardCharsets.UTF_8))
                .addLast(new StringEncoder(StandardCharsets.UTF_8))
                .addLast(new CustomIdleStateHandler(60,0,0,TimeUnit.SECONDS,1))
                .addLast(sleepStateHandler)  // 单例 @Sharable
                .addLast(serverHandler);
    }
}
