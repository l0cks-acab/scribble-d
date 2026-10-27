package com.lach.scribbled.net;

import com.lach.scribbled.Board;
import com.lach.scribbled.Scribbled;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Paint bucket fill from a board-global packed point. */
public record FillPayload(Board board, int ink, int point) implements CustomPacketPayload {
    public static final Type<FillPayload> TYPE = new Type<>(Scribbled.id("fill"));
    public static final StreamCodec<ByteBuf, FillPayload> STREAM_CODEC = StreamCodec.composite(
            Board.STREAM_CODEC, FillPayload::board,
            ByteBufCodecs.INT, FillPayload::ink,
            ByteBufCodecs.VAR_INT, FillPayload::point,
            FillPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
