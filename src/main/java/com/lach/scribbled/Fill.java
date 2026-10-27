package com.lach.scribbled;

import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import org.jetbrains.annotations.Nullable;

/** Paint bucket: 4-way flood fill over a whole board by exact colour, stopping at missing cells. */
public final class Fill {
    private Fill() {}

    /**
     * Fills from a board-global pixel. Returns whether anything changed; touched blocks are listed in {@code changed}
     * with their drawing from before the fill when {@code before} is non-null.
     */
    public static boolean fill(BlockGetter level, Board board, int ink, int point, @Nullable Map<BlockPos, Drawing> before,
                               List<WhiteboardBlockEntity> changed) {
        if (!Canvas.validInk(ink)) return false;
        int w = board.pixelWidth(), h = board.pixelHeight();
        int sx = Canvas.x(point), sy = Canvas.y(point);
        if (sx < 0 || sy < 0 || sx >= w || sy >= h) return false;
        WhiteboardBlockEntity[] cells = new WhiteboardBlockEntity[board.width() * board.height()];
        for (int r = 0; r < board.height(); r++)
            for (int c = 0; c < board.width(); c++) cells[r * board.width() + c] = board.at(level, c, r);
        WhiteboardBlockEntity start = cell(cells, board, sx, sy);
        if (start == null) return false;
        int target = rgbAt(start, sx, sy);
        int fillRgb = ink == 0 ? Canvas.BOARD_RGB : ink & 0xFFFFFF;
        if (target == fillRgb) return false;

        boolean[] seen = new boolean[w * h];
        int[] stack = new int[w * h];
        int sp = 0;
        stack[sp++] = sy * w + sx;
        seen[sy * w + sx] = true;
        int[][] hits = new int[cells.length][];
        int[] counts = new int[cells.length];
        while (sp > 0) {
            int p = stack[--sp], x = p % w, y = p / w;
            int ci = (y / Canvas.SIZE) * board.width() + x / Canvas.SIZE;
            if (hits[ci] == null) hits[ci] = new int[Canvas.AREA];
            hits[ci][counts[ci]++] = (y % Canvas.SIZE) * Canvas.SIZE + x % Canvas.SIZE;
            for (int d = 0; d < 4; d++) {
                int nx = x + (d == 0 ? 1 : d == 1 ? -1 : 0), ny = y + (d == 2 ? 1 : d == 3 ? -1 : 0);
                if (nx < 0 || ny < 0 || nx >= w || ny >= h || seen[ny * w + nx]) continue;
                WhiteboardBlockEntity be = cell(cells, board, nx, ny);
                if (be == null || rgbAt(be, nx, ny) != target) continue;
                seen[ny * w + nx] = true;
                stack[sp++] = ny * w + nx;
            }
        }
        boolean any = false;
        for (int i = 0; i < cells.length; i++) {
            if (counts[i] == 0) continue;
            Drawing snap = before != null && !before.containsKey(cells[i].getBlockPos()) ? cells[i].snapshot() : null;
            if (cells[i].fillPixels(ink, hits[i], counts[i])) {
                any = true;
                changed.add(cells[i]);
                if (snap != null) before.put(cells[i].getBlockPos(), snap);
            }
        }
        return any;
    }

    @Nullable
    private static WhiteboardBlockEntity cell(WhiteboardBlockEntity[] cells, Board board, int x, int y) {
        return cells[(y / Canvas.SIZE) * board.width() + x / Canvas.SIZE];
    }

    private static int rgbAt(WhiteboardBlockEntity be, int x, int y) {
        return be.rgb(be.pixels()[(y % Canvas.SIZE) * Canvas.SIZE + x % Canvas.SIZE]);
    }
}
