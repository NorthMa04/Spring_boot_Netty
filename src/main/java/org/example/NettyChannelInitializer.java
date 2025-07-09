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

    @Override
    protected void initChannel(SocketChannel ch) {
        ch.pipeline()
                // 1）基于大括号自动拆分完整 JSON 对象
                .addLast(new JsonObjectDecoder())

                // 2）将拆分后的 ByteBuf 转 String
                .addLast(new StringDecoder(StandardCharsets.UTF_8))
                .addLast(new StringEncoder(StandardCharsets.UTF_8))

                // 3）空闲检测：60s 无读，连续 5 次才算真正空闲
                .addLast(new CustomIdleStateHandler(
                        60, 0, 0, TimeUnit.SECONDS,
                        1
                ))

                // 4）休眠 & 唤醒逻辑
                .addLast(new SleepStateHandler())

                // 5）业务处理器
                .addLast(serverHandler);
    }
}
