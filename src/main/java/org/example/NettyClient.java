package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.UUID;

public class NettyClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final String host;
    private final int    port;
    private final String clientId;

    public NettyClient(String host, int port, String clientId) {
        this.host     = host;
        this.port     = port;
        this.clientId = clientId;
    }

    /**
     * Java 8 兼容的读写
     */
    private static String loadOrCreateClientId() throws Exception {
        Path idFile = Paths.get("client.id");
        if (Files.exists(idFile)) {
            // Java 8: 读二进制，再转 String
            byte[] bytes = Files.readAllBytes(idFile);
            return new String(bytes, StandardCharsets.UTF_8).trim();
        } else {
            String uuid = UUID.randomUUID().toString();
            // Java 8: 写字节数组
            Files.write(idFile,
                    uuid.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE);
            return uuid;
        }
    }

    public void start() throws Exception {
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap bootstrap = new Bootstrap()
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ChannelPipeline p = ch.pipeline();
                            p.addLast(new StringDecoder(StandardCharsets.UTF_8));
                            p.addLast(new StringEncoder(StandardCharsets.UTF_8));
                            p.addLast(new ClientHandler());
                        }
                    });

            Channel channel = bootstrap.connect(host, port)
                    .sync()
                    .channel();
            System.out.printf("Connected to %s:%d with clientId=%s%n",
                    host, port, clientId);

            // 独立线程处理控制台输入
            Thread consoleThread = new Thread(() -> {
                try {
                    BufferedReader console =
                            new BufferedReader(new InputStreamReader(System.in));
                    System.out.print("请输入 JSON 文件绝对路径（或 quit 退出）：");
                    String path;
                    while ((path = console.readLine()) != null) {
                        path = path.trim();
                        if ("quit".equalsIgnoreCase(path)) {
                            channel.close();
                            break;
                        }
                        // 读取并包装 JSON
                        byte[] bytes =
                                Files.readAllBytes(Paths.get(path));
                        String rawJson = new String(bytes, StandardCharsets.UTF_8);
                        JsonNode dataNode = MAPPER.readTree(rawJson);
                        ObjectNode wrapper = MAPPER.createObjectNode();
                        //wrapper.put("clientId", clientId);
                        wrapper.set("data", dataNode);
                        String toSend = MAPPER.writeValueAsString(dataNode);

                        channel.writeAndFlush(toSend);
                        System.out.print("请输入下一个 JSON 文件路径（或 quit 退出）：");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            consoleThread.setDaemon(true);
            consoleThread.start();

            channel.closeFuture().sync();
        } finally {
            group.shutdownGracefully();
        }
    }

    public static void main(String[] args) throws Exception {
        String host     = args.length > 0 ? args[0] : "127.0.0.1";
        int    port     = args.length > 1 ? Integer.parseInt(args[1]) : 8080;
        String clientId = loadOrCreateClientId();
        new NettyClient(host, port, clientId).start();
    }
}
