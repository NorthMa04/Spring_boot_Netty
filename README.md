## 一、项目概述

本项目基于 Spring Boot 与 Netty 构建了一个高可用 TCP 服务端，核心功能包括：

1.接收客户端发送的纯 JSON 消息

2.按消息中的 clientID 动态创建/检查 MySQL 表

3.将解析后的 time、clientID、quality、mass 写入对应表

4.基于全局队列实现通道“休眠—唤醒”管理

5.定期打印当前工作通道与休眠通道数量

该方案避免了每个通道单独定时器的资源浪费，提升了可维护性和系统扩展性。



## 二、技术栈与依赖

Java	1.8	运行环境

Spring Boot	2.7.12	容器管理与依赖注入

Maven	3.x	构建与依赖管理

Netty	4.1.94.Final	异步网络框架

Jackson Databind	最新	JSON 序列化/反序列化

MySQL Connector/J	8.0.18	JDBC 驱动

Spring Boot Starter JDBC	–	JDBC 模板与 HikariCP

HikariCP	默认	数据源连接池

&nbsp;



## 三、项目结构

src/main/java/org/example/

├─ ChannelManager.java           全局通道管理与唤醒调度      

├─ CustomIdleStateHandler.java   控制空闲事件频次            

├─ SleepStateHandler.java        通道休眠/唤醒逻辑          

├─ ServerHandler.java            JSON 解析与数据库写入        

├─ NettyChannelInitializer.java  通道 Pipeline 配置          

├─ NettyConfig.java              Netty 线程池与 BootStrap 配置 

├─ NettyServerRunner.java        Spring Boot 启动后绑定服务  

└─ NettySpringBootApplication.java 入口类 

/\*\*/ DiscardingJsonFrameDecoder.java（自定义 Json 拆帧，可删）





(可选）提供了位于src/main/java/org/example/下的NettyClient和ClientHandler作为测试客户端

(可选）提供了位于根目录下的AutomaticTest.cpp作为自动生成测试.json和简单测试客户端

## 四、核心模块详解

1\. ChannelManager

描述：集中管理 workingChannels 与 sleepingChannels 两个集合

功能：

1\.接收 Handler 通知，将 Channel 在两队列间切换

2\.定时任务周期性扫描休眠通道并尝试 read() 唤醒

3\.定时打印工作/休眠通道数量



2\. CustomIdleStateHandler

描述：继承自 Netty IdleStateHandler

功能：读空闲检测，每 60s 触发一次读空闲事件，跳过前 4 次，第 5 次才抛出【相当于 300s 无读后触发】



3\. SleepStateHandler

描述：标注 @Sharable 的单例 ChannelInboundHandler

功能：

1\.收到读空闲事件后立即发休眠通知并禁用自动读

2\.通知 ChannelManager 将通道移入休眠集合

3\.在 channelRead 时恢复自动读并移回工作集合

4\.在 channelInactive 时清理状态并恢复 autoRead=true



4\. ServerHandler

描述：继承 SimpleChannelInboundHandler<String>，处理业务数据

功能：

1\.直接将接收到的 String 当作 JSON 根对象

2\.校验字段完整性：time、clientID、quality、mass

3\.解析时间戳，校验 clientID 合法性

4\.动态创建表并写入数据



5\. NettyChannelInitializer

描述：Spring 注入单例，用于初始化每个新连接的 Pipeline

Pipeline 顺序：

1\.JsonObjectDecoder (按大括号拆分)

2\.StringDecoder / StringEncoder

3\.CustomIdleStateHandler

4\.SleepStateHandler

5\.ServerHandler

## 

## 五、数据与流程

1\.客户端建立 TCP 连接 → Channel 激活 → 加入 workingChannels

2\.持续读取数据正常处理

3\.若 5 次 × 60s 未读到数据 → CustomIdleStateHandler 抛出空闲事件

4\.SleepStateHandler 捕获事件：

发送休眠提示

5\.禁用 autoRead，移入 sleepingChannels

6\.ChannelManager 统一调度：首次延迟 30s，之后每 15s 调用 ch.read()

7\.若此时客户端 Buffer 有数据 → 触发 channelRead → 唤醒流程

唤醒：恢复 autoRead，移回 workingChannels，继续业务处理



## 六、配置说明(第一次Pull时需要动态修改）

application.properties 示例：

spring.datasource.url=jdbc:mysql://localhost:3306/northma?useUnicode=true\&characterEncoding=UTF-8\&serverTimezone=UTC***//根据目标数据库的端口号、database修改***

spring.datasource.username=root

spring.datasource.password=123456

spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

spring.datasource.hikari.maximum-pool-size=10

spring.datasource.hikari.minimum-idle=2

netty.port=8080

logging.level.org.example=INFO



关键参数：

readerIdleTime = 60s、skipCount = 5，即每60s检测一次是否在Working，连续五次不在working进入休眠状态。

全局唤醒扫描：首次 30s，之后 15S，即从休眠到第一次检测唤醒时间为30s，随后每两次检测之间间隔为15s.

统计打印：首次 30s，之后 30s



## 七、常见问题与排查

1、断开后无法重连

确认 SleepStateHandler 标注了 @Sharable

检查 channelInactive 中是否移除并恢复了 autoRead=true

2、JSON 拆包或粘包异常

如有特殊格式，可替换 JsonObjectDecoder 为自定义 DiscardingJsonFrameDecoder

3、表名冲突或异常

确保 clientID 只包含字母数字下划线

检查数据库用户权限与字符集



## 八、客户端配置要求

1、Upload,Download协议全部隐式或显式使用TCP传输协议

2、使用UTF-8标准格式进行传输

3、自定义客户端发送的.json数据文件，需要同时修改server端对于文件的校验，当前默认格式：

{

 "time": "2025-07-14 11:46:04",

 "clientID":"client344158",

 "quality": "Good",

 "mass": 2

}

\*\*4、推荐clientID使用String类传输和解析，经测试int类容易出现传输问题



