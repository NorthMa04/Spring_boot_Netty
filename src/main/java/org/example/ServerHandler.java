package org.example;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.*;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.util.AttributeKey;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Sharable
@Component
public class ServerHandler extends SimpleChannelInboundHandler<String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ConcurrentHashMap<String, BufferedWriter> writers = new ConcurrentHashMap<>();
    private static final AttributeKey<String> CLIENT_ID_KEY = AttributeKey.valueOf("clientId");

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        if (msg == null || msg.trim().isEmpty()) {
            ctx.writeAndFlush("非法文件\n");
            return;
        }

        JsonNode root;
        try {
            root = MAPPER.readTree(msg);
        } catch (JsonProcessingException e) {
            ctx.writeAndFlush("非法文件\n");
            return;
        } catch (IOException e) {
            ctx.writeAndFlush("内部错误\n");
            return;
        }

        if (!root.hasNonNull("clientId")) {
            ctx.writeAndFlush("非法客户端\n");
            return;
        }
        String clientId = root.get("clientId").asText();
        // 把 clientId 绑定到这条 Channel
        ctx.channel().attr(CLIENT_ID_KEY).set(clientId);

        JsonNode dataNode = root.get("data");
        if (dataNode == null || !dataNode.isObject()) {
            ctx.writeAndFlush("非法文件\n");
            return;
        }

        // 校验第一个字段名必须是 "Time"
        Iterator<String> nameIt = dataNode.fieldNames();
        if (!nameIt.hasNext() || !"Time".equals(nameIt.next())) {
            ctx.writeAndFlush("非法文件\n");
            return;
        }

        // 格式化输出
        StringBuilder out = new StringBuilder();
        Iterator<Map.Entry<String, JsonNode>> fields = dataNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String key = entry.getKey();
            JsonNode vnode = entry.getValue();
            String val = vnode.isTextual() ? vnode.asText() : vnode.toString();
            out.append(key)
                    .append(": ")
                    .append(val)
                    .append(System.lineSeparator());
        }

        System.out.println("====== 从 clientId=" + clientId + " 收到数据 ======");
        System.out.print(out);
        System.out.println("============================================");

        // 写入对应 clientId 日志文件
        try {
            BufferedWriter writer = writers.computeIfAbsent(clientId, id -> {
                try {
                    File dir = new File("logs");
                    if (!dir.exists()) dir.mkdirs();
                    return new BufferedWriter(
                            new FileWriter(new File(dir, id + ".txt"), true)
                    );
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            });
            synchronized (writer) {
                writer.write(out.toString());
                writer.flush();
            }
            ctx.writeAndFlush("已写入日志 for clientId=" + clientId + "\n");
        } catch (Exception ex) {
            ex.printStackTrace();
            ctx.writeAndFlush("内部错误\n");
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.err.println("通道异常，关闭连接：" + cause.getMessage());
        cause.printStackTrace();
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // 客户端断开时，清理对应 writer
        String clientId = ctx.channel().attr(CLIENT_ID_KEY).get();
        if (clientId != null) {
            BufferedWriter w = writers.remove(clientId);
            if (w != null) {
                try {
                    w.close();
                    System.out.println("已关闭并移除 clientId=" + clientId + " 对应的 writer");
                } catch (IOException e) {
                    System.err.println("关闭 writer 失败: clientId=" + clientId);
                    e.printStackTrace();
                }
            }
        }
        super.channelInactive(ctx);
    }
}
