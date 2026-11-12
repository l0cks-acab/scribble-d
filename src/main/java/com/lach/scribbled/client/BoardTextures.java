package com.lach.scribbled.client;

import com.lach.scribbled.Canvas;
import com.lach.scribbled.Scribbled;
import com.lach.scribbled.WhiteboardBlockEntity;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/** One dynamic texture per loaded board, re-uploaded when the board's version moves. */
public final class BoardTextures {
    private static final Map<WhiteboardBlockEntity, Entry> ENTRIES = new IdentityHashMap<>();
    private static int nextId;

    private BoardTextures() {}

    public static Identifier get(WhiteboardBlockEntity be) {
        Entry e = ENTRIES.computeIfAbsent(be, b -> {
            Entry n = new Entry(new DynamicTexture(() -> "scribbled board", Canvas.SIZE, Canvas.SIZE, false), Scribbled.id("board/" + nextId++));
            Minecraft.getInstance().getTextureManager().register(n.location, n.texture);
            return n;
        });
        if (e.version != be.version()) {
            e.version = be.version();
            NativeImage img = e.texture.getPixels();
            byte[] px = be.pixels();
            int[] argb = new int[256];
            for (int i = 0; i < 256; i++) argb[i] = 0xFF000000 | be.rgb(i);
            for (int i = 0; i < Canvas.AREA; i++) img.setPixel(i % Canvas.SIZE, i / Canvas.SIZE, argb[px[i] & 0xFF]);
            e.texture.upload();
        }
        return e.location;
    }

    public static void release(WhiteboardBlockEntity be) {
        Entry e = ENTRIES.remove(be);
        if (e != null) Minecraft.getInstance().getTextureManager().release(e.location);
    }

    public static void releaseAll() {
        for (Entry e : ENTRIES.values()) Minecraft.getInstance().getTextureManager().release(e.location);
        ENTRIES.clear();
    }

    private static final class Entry {
        final DynamicTexture texture;
        final Identifier location;
        int version = Integer.MIN_VALUE;

        Entry(DynamicTexture texture, Identifier location) {
            this.texture = texture;
            this.location = location;
        }
    }
}
