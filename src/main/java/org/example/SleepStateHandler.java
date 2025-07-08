package org.example;

import io.netty.channel.*;
import io.netty.handler.timeout.IdleStateEvent;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class SleepStateHandler extends ChannelInboundHandlerAdapter {

    private volatile boolean sleeping;
    private ScheduledFuture<?> wakeupTask;

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt)
            throws Exception {
        if (evt instanceof IdleStateEvent && !sleeping) {
            sleeping = true;
            ctx.channel().config().setAutoRead(false);
            System.out.println(">> 进入休眠: " + ctx.channel());

            // 在本 EventLoop 上每 30s 调用一次 read()，保证最迟 30s 唤醒
            wakeupTask = ctx.executor().scheduleAtFixedRate(
                    () -> {
                        if (ctx.channel().isActive()) {
                            ctx.channel().read();
                        }
                    },
                    30, 30, TimeUnit.SECONDS
            );
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg)
            throws Exception {
        if (sleeping) {
            sleeping = false;
            if (wakeupTask != null) {
                wakeupTask.cancel(false);
                wakeupTask = null;
            }
            ctx.channel().config().setAutoRead(true);
            System.out.println("<< 唤醒 Channel: " + ctx.channel());
        }
        super.channelRead(ctx, msg);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx)
            throws Exception {
        // 通道关闭或 handler 被移除时，取消定时调度
        if (wakeupTask != null) {
            wakeupTask.cancel(false);
            wakeupTask = null;
        }
        super.handlerRemoved(ctx);
    }
}
