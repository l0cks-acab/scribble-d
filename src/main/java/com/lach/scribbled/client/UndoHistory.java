package com.lach.scribbled.client;

import com.lach.scribbled.WhiteboardBlockEntity;
import com.lach.scribbled.net.RestorePayload;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;

/** This player's recent strokes, newest first, as block snapshots to put back. */
public final class UndoHistory {
    private static final int LIMIT = 30;
    private static final Deque<Map<BlockPos, byte[]>> STEPS = new ArrayDeque<>();

    private UndoHistory() {}

    public static void push(Map<BlockPos, byte[]> step) {
        if (step.isEmpty()) return;
        STEPS.push(Map.copyOf(step));
        while (STEPS.size() > LIMIT) STEPS.removeLast();
    }

    public static boolean undo() {
        Map<BlockPos, byte[]> step = STEPS.poll();
        var level = Minecraft.getInstance().level;
        if (step == null || level == null) return false;
        step.forEach((pos, pixels) -> {
            if (level.getBlockEntity(pos) instanceof WhiteboardBlockEntity be) be.restore(pixels);
            PacketDistributor.sendToServer(new RestorePayload(pos, pixels));
        });
        return true;
    }

    public static void clear() {
        STEPS.clear();
    }
}
