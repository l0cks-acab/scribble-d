package com.lach.scribbled;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Immutable snapshot of a block's pixels and custom palette: carried on items, and used for undo. */
public final class Drawing {
    private static final Codec<byte[]> BYTES = Codec.BYTE_BUFFER.xmap(b -> {
        byte[] a = new byte[b.remaining()];
        b.get(a);
        return a;
    }, ByteBuffer::wrap);
    /** Also reads the 0.1 format, which was the bare pixel array. */
    public static final Codec<Drawing> CODEC = Codec.withAlternative(RecordCodecBuilder.create(i -> i.group(
            BYTES.fieldOf("pixels").forGetter(d -> d.pixels),
            Codec.INT.listOf().optionalFieldOf("palette", List.of()).forGetter(d -> Arrays.stream(d.palette).boxed().toList())
    ).apply(i, (px, pal) -> new Drawing(px, pal.stream().mapToInt(Integer::intValue).toArray()))),
            BYTES.xmap(px -> new Drawing(px, new int[0]), d -> d.pixels));
    public static final StreamCodec<ByteBuf, Drawing> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.byteArray(Canvas.AREA), d -> d.pixels,
            ByteBufCodecs.INT.apply(ByteBufCodecs.list(Canvas.MAX_CUSTOM)), d -> Arrays.stream(d.palette).boxed().toList(),
            (px, pal) -> new Drawing(px, pal.stream().mapToInt(Integer::intValue).toArray()));

    private final byte[] pixels;
    private final int[] palette;

    public Drawing(byte[] pixels, int[] palette) {
        this.pixels = Arrays.copyOf(pixels, Canvas.AREA);
        this.palette = Arrays.copyOf(palette, Math.min(palette.length, Canvas.MAX_CUSTOM));
    }

    public byte[] copyPixels() {
        return pixels.clone();
    }

    public int[] copyPalette() {
        return palette.clone();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Drawing d && Arrays.equals(pixels, d.pixels) && Arrays.equals(palette, d.palette);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(pixels) + Arrays.hashCode(palette);
    }
}
