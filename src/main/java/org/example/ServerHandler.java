package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ServerHandler extends SimpleChannelInboundHandler<String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    // 持久化写文件所用
    private static final ConcurrentHashMap<String, BufferedWriter> writers = new ConcurrentHashMap<>();

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        try {
            // 1. 解析 JSON，提取 clientId 和 data
            JsonNode root     = MAPPER.readTree(msg);
            String clientId   = root.get("clientId").asText();
            JsonNode dataNode = root.get("data");

            // 2. 解包 dataNode 并格式化为 key: value 多行
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

            // 3. 在控制台打印处理结果
            System.out.println("====== 从 clientId=" + clientId + " 收到数据 ======");
            System.out.print(out.toString());
            System.out.println("============================================");

            // 4. （可选）继续写入文件
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

            // 5. 向客户端确认
            ctx.writeAndFlush("已写入日志 for clientId=" + clientId + "\n");

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}