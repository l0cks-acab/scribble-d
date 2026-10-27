package com.lach.scribbled.net;

import com.lach.scribbled.Board;
import com.lach.scribbled.Scribbled;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ClearPayload(Board board) implements CustomPacketPayload {
    public static final Type<ClearPayload> TYPE = new Type<>(Scribbled.id("clear"));
    public static final StreamCodec<ByteBuf, ClearPayload> STREAM_CODEC = Board.STREAM_CODEC.map(ClearPayload::new, ClearPayload::board);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
