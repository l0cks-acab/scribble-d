package com.lach.scribbled.client;

import com.lach.scribbled.Board;
import com.lach.scribbled.Canvas;
import com.lach.scribbled.MarkerItem;
import com.lach.scribbled.Scribbled;
import com.lach.scribbled.WhiteboardBlockEntity;
import com.lach.scribbled.net.ClearPayload;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

public class WhiteboardScreen extends Screen {
    private static final int[] BRUSHES = {1, 2, 3, 5, 8};
    private static final int CELL = 18;

    private final Board board;
    private int color = 16; // black
    private int brush = 1;
    private float scale;
    private int left, top, spanW, spanH;
    private int paletteX, brushX;

    @Nullable
    private StrokeSession stroke;

    public WhiteboardScreen(Board board) {
        super(Component.translatable("screen.scribbled.title"));
        this.board = board;
    }

    @Override
    protected void init() {
        if (minecraft != null && minecraft.player != null) {
            ItemStack held = minecraft.player.getMainHandItem();
            if (held.is(Scribbled.MARKER.get())) color = MarkerItem.color(held);
            else if (held.is(Scribbled.ERASER.get())) color = 0;
        }
        int side = 2 * CELL + 16;
        scale = Math.min((float) (width - 2 * side) / board.pixelWidth(), (float) (height - 24) / board.pixelHeight());
        if (scale >= 1) scale = (float) Math.floor(scale);
        spanW = Math.round(board.pixelWidth() * scale);
        spanH = Math.round(board.pixelHeight() * scale);
        left = (width - spanW) / 2;
        top = (height - spanH) / 2 + 6;
        paletteX = left - 2 * CELL - 10;
        brushX = left + spanW + 10;
        int toolsTop = Math.min(top, height - 9 * CELL - 4);
        int by = toolsTop + BRUSHES.length * CELL + 6;
        int bw = 2 * CELL + 6;
        addRenderableWidget(Button.builder(Component.translatable("screen.scribbled.undo"), b -> UndoHistory.undo()).bounds(brushX, by, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.scribbled.clear"), b -> clearBoard()).bounds(brushX, by + 24, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(brushX, by + 48, bw, 20).build());
    }

    private int toolsTop() {
        return Math.min(top, height - 9 * CELL - 4);
    }

    private boolean boardGone() {
        var mc = minecraft;
        if (mc == null || mc.level == null || mc.player == null) return true;
        double reach = 8 + Math.max(board.width(), board.height());
        if (mc.player.distanceToSqr(board.center()) > reach * reach) return true;
        for (int row = 0; row < board.height(); row++)
            for (int col = 0; col < board.width(); col++)
                if (board.at(mc.level, col, row) != null) return false;
        return true;
    }

    @Override
    public void tick() {
        if (boardGone()) {
            onClose();
            return;
        }
        if (stroke != null) stroke.flush();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        if (minecraft == null || minecraft.level == null) return;
        g.drawCenteredString(font, title, width / 2, top - 14, 0xFFFFFF);
        g.fill(left - 3, top - 3, left + spanW + 3, top + spanH + 3, 0xFF8A8F96);
        g.fill(left - 2, top - 2, left + spanW + 2, top + spanH + 2, 0xFFC9CDD2);
        for (int row = 0; row < board.height(); row++)
            for (int col = 0; col < board.width(); col++) {
                int x0 = left + Math.round(col * Canvas.SIZE * scale), x1 = left + Math.round((col + 1) * Canvas.SIZE * scale);
                int y0 = top + Math.round(row * Canvas.SIZE * scale), y1 = top + Math.round((row + 1) * Canvas.SIZE * scale);
                WhiteboardBlockEntity be = board.at(minecraft.level, col, row);
                if (be == null) g.fill(x0, y0, x1, y1, 0xFF3A3A3A);
                else g.blit(BoardTextures.get(be), x0, y0, x1 - x0, y1 - y0, 0, 0, Canvas.SIZE, Canvas.SIZE, Canvas.SIZE, Canvas.SIZE);
            }

        int tt = toolsTop();
        for (int i = 0; i < Canvas.COLORS; i++) {
            int x = paletteX + (i % 2) * CELL, y = tt + (i / 2) * CELL;
            g.fill(x, y, x + CELL - 2, y + CELL - 2, i == color ? 0xFFFFFFFF : 0xFF303030);
            g.fill(x + 1, y + 1, x + CELL - 3, y + CELL - 3, 0xFF000000 | Canvas.rgb(i));
            if (i == 0) g.drawCenteredString(font, "E", x + CELL / 2 - 1, y + 4, 0xFF606060);
        }
        for (int i = 0; i < BRUSHES.length; i++) {
            int x = brushX, y = tt + i * CELL;
            int w = 2 * CELL + 6;
            g.fill(x, y, x + w, y + CELL - 2, BRUSHES[i] == brush ? 0xFFFFFFFF : 0xFF303030);
            g.fill(x + 1, y + 1, x + w - 1, y + CELL - 3, 0xFF000000 | Canvas.BOARD_RGB);
            int d = Math.min(CELL - 6, BRUSHES[i] * 2), cy = y + (CELL - 2) / 2;
            g.fill(x + 5, cy - d / 2, x + 5 + d, cy - d / 2 + Math.max(1, d), color == 0 ? 0xFF9A9A9A : 0xFF000000 | Canvas.rgb(color));
            g.drawString(font, String.valueOf(BRUSHES[i]), x + w - 10, y + 4, 0xFF505050, false);
        }

        if (inCanvas(mx, my)) {
            int px = (int) ((mx - left) / scale) - (brush - 1) / 2, py = (int) ((my - top) / scale) - (brush - 1) / 2;
            int bs = Math.max(2, Math.round(brush * scale));
            g.renderOutline(left + Math.round(px * scale), top + Math.round(py * scale), bs, bs, 0x80000000);
        }
    }

    private boolean inCanvas(double mx, double my) {
        return mx >= left && my >= top && mx < left + spanW && my < top + spanH;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        int tt = toolsTop();
        for (int i = 0; i < Canvas.COLORS; i++) {
            int x = paletteX + (i % 2) * CELL, y = tt + (i / 2) * CELL;
            if (mx >= x && my >= y && mx < x + CELL - 2 && my < y + CELL - 2) {
                color = i;
                return true;
            }
        }
        for (int i = 0; i < BRUSHES.length; i++) {
            int y = tt + i * CELL;
            if (mx >= brushX && my >= y && mx < brushX + 2 * CELL + 6 && my < y + CELL - 2) {
                brush = BRUSHES[i];
                return true;
            }
        }
        if (inCanvas(mx, my) && (button == 0 || button == 1)) {
            if (stroke != null) stroke.end();
            stroke = new StrokeSession(board, button == 1 ? 0 : color, brush);
            stroke.add(pointAt(mx, my));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (stroke != null) {
            stroke.add(pointAt(mx, my));
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (stroke != null) {
            stroke.add(pointAt(mx, my));
            stroke.end();
            stroke = null;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_Z && hasControlDown() && !hasShiftDown()) {
            UndoHistory.undo();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    private int pointAt(double mx, double my) {
        int x = Mth.clamp((int) ((mx - left) / scale), 0, board.pixelWidth() - 1);
        int y = Mth.clamp((int) ((my - top) / scale), 0, board.pixelHeight() - 1);
        return Canvas.pack(x, y);
    }

    private void clearBoard() {
        if (minecraft == null || minecraft.level == null) return;
        Map<BlockPos, byte[]> before = new HashMap<>();
        for (int row = 0; row < board.height(); row++)
            for (int col = 0; col < board.width(); col++) {
                WhiteboardBlockEntity be = board.at(minecraft.level, col, row);
                if (be == null) continue;
                byte[] snap = be.pixels().clone();
                if (be.clear()) before.put(be.getBlockPos(), snap);
            }
        UndoHistory.push(before);
        PacketDistributor.sendToServer(new ClearPayload(board));
    }

    @Override
    public void onClose() {
        if (stroke != null) stroke.end();
        stroke = null;
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
