package org.example;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class NettyServer {

    @Autowired
    private ServerBootstrap bootstrap;

    // 从系统环境或 -Dnetty.port=8888 获取，默认为 8080
    @Value("${netty.port:8080}")
    private int port;

    public void start() throws InterruptedException {
        ChannelFuture f = bootstrap.bind(port).sync();
        System.out.println("Netty server started on port " + port);
        // 阻塞直到服务器 Channel 关闭
        f.channel().closeFuture().sync();
    }
}