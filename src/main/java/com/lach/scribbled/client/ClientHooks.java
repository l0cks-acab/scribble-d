package com.lach.scribbled.client;

import com.lach.scribbled.Board;
import com.lach.scribbled.WhiteboardBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

public final class ClientHooks {
    private ClientHooks() {}

    public static void openBoard(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        Board board = mc.level == null ? null : Board.find(mc.level, pos);
        if (board != null) mc.setScreen(new WhiteboardScreen(board));
    }

    public static void release(WhiteboardBlockEntity be) {
        BoardTextures.release(be);
    }
}
