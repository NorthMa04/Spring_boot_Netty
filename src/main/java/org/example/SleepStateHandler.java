package org.example;

import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 响应全局空闲事件：进入休眠后由 ChannelManager 统一调度唤醒。
 */
@Component
public class SleepStateHandler extends ChannelInboundHandlerAdapter {

    private static final AttributeKey<Boolean> SLEEPING_KEY =
            AttributeKey.valueOf("sleeping");

    @Autowired
    private ChannelManager channelManager;

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        // 只对读空闲事件生效，且当前未处于休眠
        if (isReaderIdle(evt) && !isSleeping(ctx)) {
            markSleeping(ctx, true);

            // 1) 发休眠提示
            ctx.writeAndFlush(Unpooled.copiedBuffer(
                            "长时间无响应，进入休眠状态\n",
                            CharsetUtil.UTF_8
                    ))
                    .addListener((ChannelFutureListener) future -> {
                        if (future.isSuccess()) {
                            // 2) 禁用自动读，移入 sleeping 队列
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
            // 唤醒：恢复自动读，移回 working 队列
            markSleeping(ctx, false);
            ctx.channel().config().setAutoRead(true);
            channelManager.addWorking(ctx.channel());
            System.out.println("<< Channel 唤醒: " + ctx.channel());
        }
        super.channelRead(ctx, msg);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        // handler 被移除或通道关闭时，确保从队列中清理
        if (isSleeping(ctx)) {
            markSleeping(ctx, false);
            channelManager.remove(ctx.channel());
        }
        super.handlerRemoved(ctx);
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
