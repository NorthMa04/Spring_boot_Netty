package org.example;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.*;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;

@ChannelHandler.Sharable
@Component
public class ServerHandler extends SimpleChannelInboundHandler<String> {
    private static final Logger logger = LoggerFactory.getLogger(ServerHandler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    // 用于通道生命周期管理的外层 clientId（不写库，仅校验）
    private static final AttributeKey<String> CLIENT_ID_KEY = AttributeKey.valueOf("clientId");
    private static final AttributeKey<Boolean> COUNTED_KEY   = AttributeKey.valueOf("counted");

    // 解析 "yyyy-MM-dd HH:mm:ss" 格式
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        logger.info("Received raw JSON: {}", msg);
        if (msg == null || msg.trim().isEmpty()) {
            ctx.writeAndFlush("非法文件1\n");
            return;
        }

        JsonNode root;
        try {
            root = MAPPER.readTree(msg);
        } catch (JsonProcessingException e) {
            ctx.writeAndFlush("非法文件2\n");
            return;
        } catch (Exception e) {
            ctx.writeAndFlush("内部错误\n");
            return;
        }

        /* 1）外层校验用 clientId
        if (!root.hasNonNull("clientId")) {
            ctx.writeAndFlush("非法客户端\n");
            return;
        }
        String authClientId = root.get("clientId").asText();
        if (ctx.channel().attr(CLIENT_ID_KEY).get() == null) {
            ctx.channel().attr(CLIENT_ID_KEY).set(authClientId);
        }
        if (Boolean.TRUE != ctx.channel().attr(COUNTED_KEY).get()) {
            ctx.channel().attr(COUNTED_KEY).set(true);
        }
*/
        // 2）内部 data 节点
        JsonNode dataNode = root.get("data");
        if (dataNode == null || !dataNode.isObject()) {
            ctx.writeAndFlush("非法文件3\n");
            return;
        }

        // 要求必须包含 time、ID、quality
        JsonNode timeNode    = dataNode.get("time");
        JsonNode idNode      = dataNode.get("clientID");
        JsonNode qualityNode = dataNode.get("quality");
        JsonNode massNode    = dataNode.get("mass");
        if (timeNode == null || idNode == null || qualityNode == null||massNode==null) {
            ctx.writeAndFlush("非法文件4\n");
            return;
        }

        String timeStr  = timeNode.asText();
        String jsonId   = idNode.asText();       // 改用 data.ID
        String quality  = qualityNode.asText();
        int mass  = massNode.asInt();

        // 解析时间
        Timestamp ts;
        try {
            LocalDateTime ldt = LocalDateTime.parse(timeStr, DTF);
            ts = Timestamp.valueOf(ldt);
        } catch (Exception ex) {
            ctx.writeAndFlush("时间格式错误\n");
            return;
        }
        if (!jsonId.matches("[0-9A-Za-z_]+")) {
            logger.warn("[EARLY RETURN] 不合法的 clientID 作为表名: {}", jsonId);
            ctx.writeAndFlush("非法 clientID\n");
            return;
        }
  String tableName= jsonId ;
        String createTableSql = ""
                + "CREATE TABLE IF NOT EXISTS `" + tableName + "` ("
                + "  `id` BIGINT NOT NULL AUTO_INCREMENT,"
                + "  `time` DATETIME NOT NULL,"
                + "  `clientID` VARCHAR(50) NOT NULL,"
                + "  `quality` VARCHAR(100) NOT NULL,"
                + "  `mass` INT NOT NULL,"
                + "  PRIMARY KEY (`id`)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";
        try {
            logger.info("建表检查/创建：{}", tableName);
            jdbcTemplate.execute(createTableSql);
        } catch (Exception ex) {
            logger.error("[EARLY RETURN] 创建表失败：{}", tableName, ex);
            ctx.writeAndFlush("建表失败\n");
            return;
        }
        // 3）写库：把 time、data.ID (映射到 client_id)、quality 写入 data_log
        String sql = "INSERT INTO `"
                + tableName
                + "` (`time`, `clientID`, `quality`, `mass`) "
                + "VALUES (?, ?, ?, ?)";
        try {
            jdbcTemplate.update(sql, ts, jsonId, quality,mass);
            ctx.writeAndFlush("已写入数据库：ID=" + jsonId + "\n");
        } catch (Exception ex) {
            ex.printStackTrace();
            ctx.writeAndFlush("数据库写入失败\n");
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.err.println("通道异常，关闭连接：" + cause.getMessage());
        cause.printStackTrace();
        ctx.close();
    }
}
