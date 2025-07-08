package org.example;

import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.CharsetUtil;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class SleepStateHandler extends ChannelInboundHandlerAdapter {

    private volatile boolean sleeping;
    private ScheduledFuture<?> wakeupTask;

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt)
            throws Exception {
        // 只对读空闲事件生效
        if (evt instanceof IdleStateEvent
                && ((IdleStateEvent) evt).state() == IdleStateEvent.READER_IDLE_STATE_EVENT.state()
                && !sleeping) {

            sleeping = true;

            // 1）先把通知写出去
            ChannelFuture writeFuture =ctx.writeAndFlush(Unpooled.copiedBuffer("长时间无响应，进入休眠状态\n", CharsetUtil.UTF_8));

            // 2）在写成功后再真正禁读并调度唤醒
            writeFuture.addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    // 禁止自动读，进入休眠
                    ctx.channel().config().setAutoRead(false);
                    System.out.println(">> Channel 进入休眠: " + ctx.channel());

                    // 每 10 分钟唤醒一次
                    wakeupTask = ctx.executor().scheduleAtFixedRate(
                            () -> {
                                if (ctx.channel().isActive()) {
                                    ctx.channel().read();
                                    System.out.println("** 唤醒尝试 read(): " + ctx.channel());
                                }
                            },
                            30,  // 首次延迟 10 分钟
                            30,  // 之后每隔 10 分钟
                            TimeUnit.SECONDS
                    );
                } else {
                    // 如果发送失败，打印原因
                    System.err.println("休眠通知发送失败");
                    future.cause().printStackTrace();
                }
            });

        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg)
            throws Exception {
        if (sleeping) {
            sleeping = false;
            // 收到真正的数据，取消后续定时唤醒并恢复 autoRead
            if (wakeupTask != null) {
                wakeupTask.cancel(false);
                wakeupTask = null;
            }
            ctx.channel().config().setAutoRead(true);
            System.out.println("<< Channel 唤醒: " + ctx.channel());
        }
        super.channelRead(ctx, msg);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx)
            throws Exception {
        if (wakeupTask != null) {
            wakeupTask.cancel(false);
            wakeupTask = null;
        }
        super.handlerRemoved(ctx);
    }
}
