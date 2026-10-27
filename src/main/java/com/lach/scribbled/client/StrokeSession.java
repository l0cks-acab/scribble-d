package com.lach.scribbled.client;

import com.lach.scribbled.Board;
import com.lach.scribbled.Canvas;
import com.lach.scribbled.WhiteboardBlockEntity;
import com.lach.scribbled.net.PaintPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * One continuous stroke on a board: paints locally for instant feedback, batches points to the server,
 * and records each touched block's pixels from before the stroke so it can be undone.
 */
public final class StrokeSession {
    private final Board board;
    private final int color, size;
    private final List<Integer> pending = new ArrayList<>();
    private final Map<BlockPos, byte[]> before = new HashMap<>();
    private int last = -1;
    /** True once part of the stroke has been sent, so a lone carried-over point isn't resent as a dot. */
    private boolean continued;

    public StrokeSession(Board board, int color, int size) {
        this.board = board;
        this.color = color;
        this.size = size;
    }

    public Board board() {
        return board;
    }

    public void add(int point) {
        if (point == last) return;
        List<Integer> seg = last < 0 ? List.of(point) : List.of(last, point);
        var level = Minecraft.getInstance().level;
        if (level != null)
            for (int row = 0; row < board.height(); row++)
                for (int col = 0; col < board.width(); col++) {
                    WhiteboardBlockEntity be = board.at(level, col, row);
                    if (be == null) continue;
                    byte[] snap = before.containsKey(be.getBlockPos()) ? null : be.pixels().clone();
                    if (be.paint(color, size, seg, col * Canvas.SIZE, row * Canvas.SIZE) && snap != null)
                        before.put(be.getBlockPos(), snap);
                }
        last = point;
        pending.add(point);
        if (pending.size() >= PaintPayload.MAX_POINTS) flush();
    }

    public void flush() {
        if (pending.isEmpty() || continued && pending.size() == 1 || Minecraft.getInstance().getConnection() == null) return;
        PacketDistributor.sendToServer(new PaintPayload(board, color, size, List.copyOf(pending)));
        pending.clear();
        pending.add(last);
        continued = true;
    }

    /** Sends what's left and files the stroke in the undo history. */
    public void end() {
        flush();
        UndoHistory.push(before);
    }
}
