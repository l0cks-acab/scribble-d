package com.lach.scribbled.net;

import com.lach.scribbled.Board;
import com.lach.scribbled.Scribbled;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** A brush stroke segment: board-global packed points joined by lines. */
public record PaintPayload(Board board, int ink, int size, List<Integer> points) implements CustomPacketPayload {
    public static final int MAX_POINTS = 256;
    public static final Type<PaintPayload> TYPE = new Type<>(Scribbled.id("paint"));
    public static final StreamCodec<ByteBuf, PaintPayload> STREAM_CODEC = StreamCodec.composite(
            Board.STREAM_CODEC, PaintPayload::board,
            ByteBufCodecs.INT, PaintPayload::ink,
            ByteBufCodecs.VAR_INT, PaintPayload::size,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(MAX_POINTS)), PaintPayload::points,
            PaintPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
