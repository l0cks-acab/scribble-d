package com.lach.scribbled.client;

import com.lach.scribbled.Board;
import com.lach.scribbled.Canvas;
import com.lach.scribbled.MarkerItem;
import com.lach.scribbled.Scribbled;
import com.lach.scribbled.WhiteboardBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** Draws on boards in the world while use is held with a marker or eraser. Sampled every frame for smooth lines. */
public final class MarkerDrawing {
    public static final int MARKER_SIZE = 2, FINE_SIZE = 1, ERASER_SIZE = 6;

    @Nullable
    private static StrokeSession session;

    private MarkerDrawing() {}

    static void frame() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null || !mc.options.keyUse.isDown()) {
            end();
            return;
        }
        ItemStack held = mc.player.getMainHandItem();
        boolean eraser = held.is(Scribbled.ERASER.get());
        if (!eraser && !held.is(Scribbled.MARKER.get())
                || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != BlockHitResult.Type.BLOCK) {
            end();
            return;
        }
        BlockState state = mc.level.getBlockState(hit.getBlockPos());
        if (!(state.getBlock() instanceof WhiteboardBlock) || hit.getDirection() != state.getValue(WhiteboardBlock.FACING)) {
            end();
            return;
        }
        if (session == null || !session.board().contains(hit.getBlockPos())) {
            end();
            Board board = Board.find(mc.level, hit.getBlockPos());
            if (board == null) return;
            int size = eraser ? ERASER_SIZE : mc.player.isShiftKeyDown() ? FINE_SIZE : MARKER_SIZE;
            session = new StrokeSession(board, eraser ? 0 : Canvas.ink(MarkerItem.color(held)), size);
        }
        session.add(session.board().pixelAt(hit.getBlockPos(), hit.getLocation()));
    }

    static void tick() {
        if (session != null) session.flush();
    }

    static void end() {
        if (session != null) session.end();
        session = null;
    }
}
