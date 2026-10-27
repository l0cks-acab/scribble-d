package com.lach.scribbled.net;

import com.lach.scribbled.Drawing;
import com.lach.scribbled.Scribbled;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Puts one block's drawing back to an earlier snapshot (undo). */
public record RestorePayload(BlockPos pos, Drawing drawing) implements CustomPacketPayload {
    public static final Type<RestorePayload> TYPE = new Type<>(Scribbled.id("restore"));
    public static final StreamCodec<ByteBuf, RestorePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, RestorePayload::pos,
            Drawing.STREAM_CODEC, RestorePayload::drawing,
            RestorePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
