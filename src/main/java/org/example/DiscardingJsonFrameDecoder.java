package org.example;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

public class DiscardingJsonFrameDecoder extends ByteToMessageDecoder {
    private boolean inString;
    private boolean escape;

    @Override
    protected void decode(ChannelHandlerContext ctx,
                          ByteBuf in,
                          List<Object> out) throws Exception {
        int readerIndex = in.readerIndex();
        int writerIndex = in.writerIndex();

        int depth = 0;
        int frameStart = -1;

        // 扫描字节，找第一个完整 {} 帧
        for (int i = readerIndex; i < writerIndex; i++) {
            byte b = in.getByte(i);

            if (b == '"' && !escape) {
                inString = !inString;
            }
            // 处理转义符
            escape = (!escape && b == '\\');

            if (!inString) {
                // 记录第一个 '{' 位置
                if (b == '{') {
                    if (depth == 0) {
                        frameStart = i;
                    }
                    depth++;
                } else if (b == '}') {
                    if (depth > 0) {
                        depth--;
                        // depth 回到 0，找到完整帧的结束位置
                        if (depth == 0 && frameStart >= 0) {
                            int frameLength = i - frameStart + 1;

                            // 丢弃 frameStart 之前的半包数据
                            if (frameStart > readerIndex) {
                                in.skipBytes(frameStart - readerIndex);
                            }

                            // 切出完整帧
                            ByteBuf frame = in.readRetainedSlice(frameLength);
                            out.add(frame);
                            return; // 只拆出一个帧，下一轮 decode 再来
                        }
                    }
                }
            }
        }
        // depth>0 或 无 '{'：不够一个完整帧，等待更多数据
    }
}
