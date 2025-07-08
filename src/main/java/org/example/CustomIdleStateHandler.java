package org.example;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class CustomIdleStateHandler extends IdleStateHandler {
    private final int skipCount;
    private final AtomicInteger counter = new AtomicInteger();

    public CustomIdleStateHandler(long readerIdleTime,
                                  long writerIdleTime,
                                  long allIdleTime,
                                  TimeUnit unit,
                                  int skipCount) {
        super(readerIdleTime, writerIdleTime, allIdleTime, unit);
        this.skipCount = skipCount;
    }

    @Override
    protected void channelIdle(ChannelHandlerContext ctx, IdleStateEvent evt) throws Exception {
        if (evt.state() == IdleStateEvent.READER_IDLE_STATE_EVENT.state()) {
            if (counter.incrementAndGet() % skipCount != 0) {
                return;
            }
        }
        super.channelIdle(ctx, evt);
    }
}
