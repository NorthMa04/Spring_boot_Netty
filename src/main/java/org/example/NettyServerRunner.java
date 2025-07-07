package org.example;


import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
@Component
public class NettyServerRunner implements ApplicationRunner {

    private final ServerBootstrap bootstrap;
    private final int port = 8080;  // 或用 @Value("${netty.port}") 注入

    public NettyServerRunner(ServerBootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        ChannelFuture f = bootstrap.bind(port).sync();
        System.out.println("Netty started on port " + port);
        // 不阻塞主线程，交给 JVM 退出钩子来关闭
        f.channel().closeFuture().addListener(cf -> System.out.println("Netty stopped."));
    }
}