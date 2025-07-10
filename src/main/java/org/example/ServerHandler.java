package org.example;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.*;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.util.AttributeKey;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Sharable
@Component
public class ServerHandler extends SimpleChannelInboundHandler<String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final AttributeKey<String> CLIENT_ID_KEY   = AttributeKey.valueOf("clientId");
    private static final AttributeKey<Boolean> COUNTED_KEY    = AttributeKey.valueOf("counted");
    private static final ConcurrentHashMap<String, BufferedWriter> writers   = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, AtomicInteger>   refCounts = new ConcurrentHashMap<>();

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

        // 校验 clientId
        if (!root.hasNonNull("clientId")) {
            ctx.writeAndFlush("非法客户端\n");
            return;
        }
        String clientId = root.get("clientId").asText();

        // 1. 绑定 clientId 到 Channel（只做一次）
        if (ctx.channel().attr(CLIENT_ID_KEY).get() == null) {
            ctx.channel().attr(CLIENT_ID_KEY).set(clientId);
        }

        // 2. 引用计数：首次绑定后自增
        if (Boolean.TRUE != ctx.channel().attr(COUNTED_KEY).get()) {
            ctx.channel().attr(COUNTED_KEY).set(true);
            refCounts.compute(clientId, (k, v) -> {
                if (v == null) return new AtomicInteger(1);
                v.incrementAndGet();
                return v;
            });
        }

        // 校验 data 节点及首字段 Time
        JsonNode dataNode = root.get("data");
        if (dataNode == null || !dataNode.isObject()) {
            ctx.writeAndFlush("非法文件\n");
            return;
        }
        Iterator<String> fn = dataNode.fieldNames();
        if (!fn.hasNext() || !"time".equals(fn.next())) {
            ctx.writeAndFlush("非法文件\n");
            return;
        }

        // 格式化输出
        StringBuilder out = new StringBuilder();
        for (Iterator<Map.Entry<String, JsonNode>> it = dataNode.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> e = it.next();
            String key = e.getKey();
            String val = e.getValue().isTextual()
                    ? e.getValue().asText()
                    : e.getValue().toString();
            out.append(key).append(": ").append(val)
                    .append(System.lineSeparator());
        }

        System.out.println("====== 从 clientId=" + clientId + " 收到数据 ======");
        System.out.print(out);
        System.out.println("============================================");

        // 写入日志
        try {
            // ServerHandler 中的 writer 初始化部分
            BufferedWriter writer = writers.computeIfAbsent(clientId, id -> {
                try {
                    File dir = new File("logs");
                    if (!dir.exists()) dir.mkdirs();
                    return new BufferedWriter(new OutputStreamWriter(
                            new FileOutputStream(new File(dir, id + ".txt"), true),
                            StandardCharsets.UTF_8
                    ));
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
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
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // 客户端断开时，引用计数-1；为0时才真正关闭并移除 writer
        String clientId = ctx.channel().attr(CLIENT_ID_KEY).get();
        if (clientId != null) {
            AtomicInteger cnt = refCounts.get(clientId);
            if (cnt != null && cnt.decrementAndGet() <= 0) {
                refCounts.remove(clientId);
                BufferedWriter w = writers.remove(clientId);
                if (w != null) {
                    try {
                        w.close();
                        System.out.println("已关闭并移除 clientId=" + clientId + " 的 writer");
                    } catch (IOException e) {
                        System.err.println("关闭 writer 失败: clientId=" + clientId);
                        e.printStackTrace();
                    }
                }
            }
        }
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.err.println("通道异常，关闭连接：" + cause.getMessage());
        cause.printStackTrace();
        ctx.close();
    }
}