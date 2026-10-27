package com.lach.scribbled;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.nio.ByteBuffer;
import java.util.Arrays;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Immutable snapshot of a board's pixels, carried on the item so drawings survive being picked up. */
public final class Drawing {
    public static final Codec<Drawing> CODEC = Codec.BYTE_BUFFER.xmap(b -> {
        byte[] a = new byte[b.remaining()];
        b.get(a);
        return new Drawing(a);
    }, d -> ByteBuffer.wrap(d.pixels));
    public static final StreamCodec<ByteBuf, Drawing> STREAM_CODEC = ByteBufCodecs.byteArray(Canvas.AREA).map(Drawing::new, d -> d.pixels);

    private final byte[] pixels;

    public Drawing(byte[] pixels) {
        this.pixels = Arrays.copyOf(pixels, Canvas.AREA);
    }

    public byte[] copyPixels() {
        return pixels.clone();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Drawing d && Arrays.equals(pixels, d.pixels);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(pixels);
    }
}
