package com.lach.scribbled;

import java.util.List;
import net.minecraft.world.item.DyeColor;

/**
 * Palette-indexed pixel canvas helpers. Index 0 is bare board, 1-16 are the dye colours.
 * Strokes use board-global coordinates packed as x | y << 16; each block draws the part that lands on it.
 */
public final class Canvas {
    public static final int SIZE = 64;
    public static final int AREA = SIZE * SIZE;
    public static final int COLORS = 17;
    public static final int MAX_BRUSH = 8;
    public static final int BOARD_RGB = 0xF4F4EE;

    private Canvas() {}

    public static int rgb(int index) {
        return index <= 0 || index >= COLORS ? BOARD_RGB : DyeColor.byId(index - 1).getTextureDiffuseColor() & 0xFFFFFF;
    }

    public static int pack(int x, int y) {
        return x | y << 16;
    }

    public static int x(int p) {
        return p & 0xFFFF;
    }

    public static int y(int p) {
        return p >>> 16;
    }

    /** Draws a polyline onto the block whose top-left pixel sits at (ox, oy); returns whether any pixel changed. */
    public static boolean stroke(byte[] px, int color, int size, List<Integer> points, int ox, int oy) {
        if (points.isEmpty() || color < 0 || color >= COLORS) return false;
        size = Math.max(1, Math.min(MAX_BRUSH, size));
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (int p : points) {
            minX = Math.min(minX, x(p));
            maxX = Math.max(maxX, x(p));
            minY = Math.min(minY, y(p));
            maxY = Math.max(maxY, y(p));
        }
        if (maxX + size < ox || minX - size >= ox + SIZE || maxY + size < oy || minY - size >= oy + SIZE) return false;
        byte c = (byte) color;
        int x0 = x(points.get(0)) - ox, y0 = y(points.get(0)) - oy;
        boolean changed = dab(px, x0, y0, size, c);
        for (int i = 1; i < points.size(); i++) {
            int x1 = x(points.get(i)) - ox, y1 = y(points.get(i)) - oy;
            changed |= line(px, x0, y0, x1, y1, size, c);
            x0 = x1;
            y0 = y1;
        }
        return changed;
    }

    private static boolean line(byte[] px, int x0, int y0, int x1, int y1, int size, byte c) {
        boolean changed = false;
        int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, err = dx + dy;
        while (true) {
            changed |= dab(px, x0, y0, size, c);
            if (x0 == x1 && y0 == y1) return changed;
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x0 += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y0 += sy;
            }
        }
    }

    private static boolean dab(byte[] px, int cx, int cy, int size, byte c) {
        if (cx < -size || cy < -size || cx >= SIZE + size || cy >= SIZE + size) return false;
        boolean changed = false;
        double mid = (size - 1) / 2.0, r2 = size * size / 4.0 + 0.25;
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                double di = i - mid, dj = j - mid;
                if (di * di + dj * dj > r2) continue;
                int x = cx + i - (size - 1) / 2, y = cy + j - (size - 1) / 2;
                if (x < 0 || y < 0 || x >= SIZE || y >= SIZE) continue;
                int k = y * SIZE + x;
                if (px[k] != c) {
                    px[k] = c;
                    changed = true;
                }
            }
        }
        return changed;
    }
}
