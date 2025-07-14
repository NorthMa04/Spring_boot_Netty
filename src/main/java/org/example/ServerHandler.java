package org.example;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
@ChannelHandler.Sharable
@Component
public class ServerHandler extends SimpleChannelInboundHandler<String> {

    private static final Logger logger = LoggerFactory.getLogger(ServerHandler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter DTF =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        logger.info("Received JSON: {}", msg);

        // 1) 非空校验
        if (msg == null || msg.trim().isEmpty()) {
            ctx.writeAndFlush("非法文件\n");
            return;
        }

        // 2) 反序列化
        JsonNode root;
        try {
            root = MAPPER.readTree(msg);
        } catch (JsonProcessingException e) {
            ctx.writeAndFlush("非法文件\n");
            return;
        }

        // 3) 从根节点直接取字段（不再有外层 data 或 UUID 包装）
        JsonNode timeNode    = root.get("time");
        JsonNode idNode      = root.get("clientID");
        JsonNode qualityNode = root.get("quality");
        JsonNode massNode    = root.get("mass");

        if (timeNode == null || idNode == null
                || qualityNode == null || massNode == null) {
            ctx.writeAndFlush("字段不完整\n");
            return;
        }

        String timeStr = timeNode.asText();
        String clientId = idNode.asText();
        String quality  = qualityNode.asText();
        int mass        = massNode.asInt();

        // 4) 时间解析
        Timestamp ts;
        try {
            LocalDateTime ldt = LocalDateTime.parse(timeStr, DTF);
            ts = Timestamp.valueOf(ldt);
        } catch (Exception ex) {
            ctx.writeAndFlush("时间格式错误\n");
            return;
        }

        // 5) clientID 校验（用作表名）
        if (!clientId.matches("[0-9A-Za-z_]+")) {
            logger.warn("非法 clientID 用作表名: {}", clientId);
            ctx.writeAndFlush("非法 clientID\n");
            return;
        }

        // 6) 动态建表
        String tableName = clientId;
        String createSql = ""
                + "CREATE TABLE IF NOT EXISTS `" + tableName + "` ("
                + "  `id` BIGINT NOT NULL AUTO_INCREMENT,"
                + "  `time` DATETIME NOT NULL,"
                + "  `clientID` VARCHAR(50) NOT NULL,"
                + "  `quality` VARCHAR(100) NOT NULL,"
                + "  `mass` INT NOT NULL,"
                + "  PRIMARY KEY (`id`)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";
        try {
            jdbcTemplate.execute(createSql);
        } catch (Exception ex) {
            logger.error("建表失败：{}", tableName, ex);
            ctx.writeAndFlush("建表失败\n");
            return;
        }

        // 7) 写入表中
        String insertSql = "INSERT INTO `" + tableName
                + "` (`time`, `clientID`, `quality`, `mass`) VALUES (?, ?, ?, ?)";
        try {
            jdbcTemplate.update(insertSql, ts, clientId, quality, mass);
            ctx.writeAndFlush("已写入数据库：ID=" + clientId + "\n");
        } catch (Exception ex) {
            logger.error("数据库写入失败", ex);
            ctx.writeAndFlush("写库失败\n");
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("通道异常，关闭连接", cause);
        ctx.close();
    }
}
