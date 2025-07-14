package org.example;

import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import io.netty.channel.ChannelHandler.Sharable;

@Sharable
@Component
public class SleepStateHandler extends ChannelInboundHandlerAdapter {

    private static final AttributeKey<Boolean> SLEEPING_KEY =
            AttributeKey.valueOf("sleeping");

    @Autowired
    private ChannelManager channelManager;

    @PostConstruct
    public void init() {
        // 确保一开始所有 handlerAdded 的通道都在 working
        // （可选，看业务是否需要一连入队）
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        // 新连接加入 working 队列
        ctx.channel().config().setAutoRead(true);
        channelManager.addWorking(ctx.channel());
        super.handlerAdded(ctx);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (isReaderIdle(evt) && !isSleeping(ctx)) {
            markSleeping(ctx, true);

            ctx.writeAndFlush(Unpooled.copiedBuffer(
                            "长时间无响应，进入休眠状态\n",
                            CharsetUtil.UTF_8
                    ))
                    .addListener((ChannelFutureListener) future -> {
                        if (future.isSuccess()) {
                            ctx.channel().config().setAutoRead(false);
                            channelManager.addSleeping(ctx.channel());
                            System.out.println(">> Channel 进入休眠: " + ctx.channel());
                        } else {
                            System.err.println("休眠通知发送失败");
                            future.cause().printStackTrace();
                        }
                    });

        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (isSleeping(ctx)) {
            // 客户端真的回数据，唤醒
            markSleeping(ctx, false);
            ctx.channel().config().setAutoRead(true);
            channelManager.addWorking(ctx.channel());
            System.out.println("<< Channel 唤醒: " + ctx.channel());
        }
        super.channelRead(ctx, msg);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // 客户端断连时，彻底清理
        if (isSleeping(ctx)) {
            markSleeping(ctx, false);
        }
        channelManager.remove(ctx.channel());
        // 恢复 autoRead，以防重连时被禁
        ctx.channel().config().setAutoRead(true);
        super.channelInactive(ctx);
    }

    private boolean isReaderIdle(Object evt) {
        return evt instanceof IdleStateEvent
                && ((IdleStateEvent) evt).state()
                == IdleStateEvent.READER_IDLE_STATE_EVENT.state();
    }

    private boolean isSleeping(ChannelHandlerContext ctx) {
        return Boolean.TRUE.equals(ctx.channel().attr(SLEEPING_KEY).get());
    }

    private void markSleeping(ChannelHandlerContext ctx, boolean sleeping) {
        ctx.channel().attr(SLEEPING_KEY).set(sleeping);
    }
}
