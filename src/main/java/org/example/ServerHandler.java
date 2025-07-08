package org.example;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
@ChannelHandler.Sharable
@Component
public class ServerHandler extends SimpleChannelInboundHandler<String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ConcurrentHashMap<String, BufferedWriter> writers = new ConcurrentHashMap<>();

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        // 0. 空消息或全空白，视为非法文件
        if (msg == null || msg.trim().isEmpty()) {
            ctx.writeAndFlush("非法文件\n");
            return;
        }

        JsonNode root;
        try {
            // 1. 解析 JSON
            root = MAPPER.readTree(msg);
        } catch (JsonProcessingException e) {
            // JSON 格式错误
            ctx.writeAndFlush("非法文件\n");
            return;
        }

        // 2. 校验 clientId
        if (!root.hasNonNull("clientId")) {
            ctx.writeAndFlush("非法客户端\n");
            return;
        }
        String clientId = root.get("clientId").asText();

        // 3. 校验 data 节点及首字段必须是 Time
        JsonNode dataNode = root.get("data");
        if (dataNode == null || !dataNode.isObject()) {
            ctx.writeAndFlush("非法文件\n");
            return;
        }
        Iterator<String> fieldNames = dataNode.fieldNames();
        if (!fieldNames.hasNext() || !"Time".equals(fieldNames.next())) {
            ctx.writeAndFlush("非法文件\n");
            return;
        }

        try {
            // 4. 解包 dataNode 并格式化为 key: value 多行
            StringBuilder out = new StringBuilder();
            Iterator<Map.Entry<String, JsonNode>> fields = dataNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> e = fields.next();
                String k = e.getKey();
                String v = e.getValue().isTextual()
                        ? e.getValue().asText()
                        : e.getValue().toString();
                out.append(k).append(": ").append(v)
                        .append(System.lineSeparator());
            }

            // 5. 控制台打印
            System.out.println("====== 从 clientId=" + clientId + " 收到数据 ======");
            System.out.print(out);
            System.out.println("============================================");

            // 6. 持久化写文件
            BufferedWriter writer = writers.computeIfAbsent(clientId, id -> {
                try {
                    File dir = new File("logs");
                    if (!dir.exists()) dir.mkdirs();
                    return new BufferedWriter(new FileWriter(
                            new File(dir, id + ".txt"), true));
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });
            synchronized (writer) {
                writer.write(out.toString());
                writer.flush();
            }

            // 7. 发送确认
            ctx.writeAndFlush("已写入日志 for clientId=" + clientId + "\n");

        } catch (Exception ex) {
            ex.printStackTrace();
            ctx.writeAndFlush("非法文件\n");
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // 出现未捕获异常时，关闭连接
        cause.printStackTrace();
        ctx.close();
    }
}
