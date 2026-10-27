package com.lach.scribbled.client;

import com.lach.scribbled.Board;
import com.lach.scribbled.Canvas;
import com.lach.scribbled.Drawing;
import com.lach.scribbled.MarkerItem;
import com.lach.scribbled.Scribbled;
import com.lach.scribbled.WhiteboardBlockEntity;
import com.lach.scribbled.net.ClearPayload;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
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
    /** Palette slot that holds the RGB picker's colour; 0-16 are eraser + dyes. */
    private static final int CUSTOM = Canvas.COLORS;
    private static final int SV = 96, HUE_W = 12, PAD = 6;

    private final Board board;
    private int color = 16; // palette slot, black
    /** HSV of the custom colour, each 0-1; kept between screens. */
    private static float hue = 0.58f, sat = 0.8f, val = 0.9f;
    private boolean picker;
    private int pickerX, pickerY;
    /** 1 while dragging in the SV square, 2 in the hue bar. */
    private int pickerDrag;
    private EditBox hex;
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
        pickerX = left + 4;
        pickerY = toolsTop;
        hex = new EditBox(font, pickerX + PAD + 12, pickerY + PAD + SV + 6, 58, 14, Component.literal("hex"));
        hex.setMaxLength(6);
        hex.setFilter(t -> t.matches("[0-9a-fA-F]{0,6}"));
        hex.setValue(String.format("%06X", customRgb()));
        hex.setResponder(t -> {
            if (t.length() == 6 && Integer.parseInt(t, 16) != customRgb()) setCustom(Integer.parseInt(t, 16), false);
        });
        hex.visible = picker;
        addWidget(hex);
        addRenderableWidget(Button.builder(Component.translatable("screen.scribbled.undo"), b -> UndoHistory.undo()).bounds(brushX, by, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.scribbled.clear"), b -> clearBoard()).bounds(brushX, by + 24, bw, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(brushX, by + 48, bw, 20).build());
    }

    private int customRgb() {
        return Mth.hsvToRgb(hue, sat, val) & 0xFFFFFF;
    }

    private void setCustom(int rgb, boolean updateHex) {
        float r = (rgb >> 16 & 255) / 255f, g = (rgb >> 8 & 255) / 255f, b = (rgb & 255) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        val = max;
        sat = max == 0 ? 0 : d / max;
        if (d > 0) {
            float h = max == r ? (g - b) / d : max == g ? 2 + (b - r) / d : 4 + (r - g) / d;
            hue = ((h / 6f) % 1f + 1f) % 1f;
        }
        color = CUSTOM;
        if (updateHex && hex != null) hex.setValue(String.format("%06X", rgb));
    }

    private int slotRgb(int slot) {
        return slot == CUSTOM ? customRgb() : Canvas.rgb(slot);
    }

    private int ink() {
        return color == CUSTOM ? 0xFF000000 | customRgb() : Canvas.ink(color);
    }

    private void togglePicker(boolean open) {
        picker = open;
        pickerDrag = 0;
        if (hex != null) {
            hex.visible = open;
            if (!open) hex.setFocused(false);
        }
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
        for (int i = 0; i <= CUSTOM; i++) {
            int x = paletteX + (i % 2) * CELL, y = tt + (i / 2) * CELL;
            g.fill(x, y, x + CELL - 2, y + CELL - 2, i == color ? 0xFFFFFFFF : 0xFF303030);
            g.fill(x + 1, y + 1, x + CELL - 3, y + CELL - 3, 0xFF000000 | slotRgb(i));
            if (i == 0) g.drawCenteredString(font, "E", x + CELL / 2 - 1, y + 4, 0xFF606060);
            if (i == CUSTOM) // rainbow strip marks the picker slot
                for (int k = 0; k < CELL - 4; k++)
                    g.fill(x + 1 + k, y + 1, x + 2 + k, y + 4, 0xFF000000 | Mth.hsvToRgb(k / (float) (CELL - 4), 1, 1));
        }
        for (int i = 0; i < BRUSHES.length; i++) {
            int x = brushX, y = tt + i * CELL;
            int w = 2 * CELL + 6;
            g.fill(x, y, x + w, y + CELL - 2, BRUSHES[i] == brush ? 0xFFFFFFFF : 0xFF303030);
            g.fill(x + 1, y + 1, x + w - 1, y + CELL - 3, 0xFF000000 | Canvas.BOARD_RGB);
            int d = Math.min(CELL - 6, BRUSHES[i] * 2), cy = y + (CELL - 2) / 2;
            g.fill(x + 5, cy - d / 2, x + 5 + d, cy - d / 2 + Math.max(1, d), color == 0 ? 0xFF9A9A9A : 0xFF000000 | slotRgb(color));
            g.drawString(font, String.valueOf(BRUSHES[i]), x + w - 10, y + 4, 0xFF505050, false);
        }

        if (inCanvas(mx, my)) {
            int px = (int) ((mx - left) / scale) - (brush - 1) / 2, py = (int) ((my - top) / scale) - (brush - 1) / 2;
            int bs = Math.max(2, Math.round(brush * scale));
            g.renderOutline(left + Math.round(px * scale), top + Math.round(py * scale), bs, bs, 0x80000000);
        }
        if (picker) renderPicker(g, mx, my, partial);
    }

    private int pickerW() {
        return PAD * 3 + SV + HUE_W;
    }

    private int pickerH() {
        return PAD * 2 + SV + 26;
    }

    private void renderPicker(GuiGraphics g, int mx, int my, float partial) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        int x = pickerX, y = pickerY;
        g.fill(x - 1, y - 1, x + pickerW() + 1, y + pickerH() + 1, 0xFF8A8F96);
        g.fill(x, y, x + pickerW(), y + pickerH(), 0xFF202226);
        int sx = x + PAD, sy = y + PAD;
        for (int i = 0; i < SV; i++) {
            int top = 0xFF000000 | Mth.hsvToRgb(hue, i / (float) (SV - 1), 1);
            g.fillGradient(sx + i, sy, sx + i + 1, sy + SV, top, 0xFF000000);
        }
        int cx = sx + Math.round(sat * (SV - 1)), cy = sy + Math.round((1 - val) * (SV - 1));
        g.renderOutline(cx - 2, cy - 2, 5, 5, val > 0.5f ? 0xFF000000 : 0xFFFFFFFF);
        int hx = sx + SV + PAD;
        for (int j = 0; j < SV; j++) g.fill(hx, sy + j, hx + HUE_W, sy + j + 1, 0xFF000000 | Mth.hsvToRgb(j / (float) SV, 1, 1));
        int hy = sy + Math.round(hue * (SV - 1));
        g.fill(hx - 2, hy - 1, hx + HUE_W + 2, hy + 2, 0xFFFFFFFF);
        g.fill(hx, hy, hx + HUE_W, hy + 1, 0xFF000000 | Mth.hsvToRgb(hue, 1, 1));
        g.drawString(font, "#", sx, sy + SV + 9, 0xFFAAAAAA, false);
        hex.render(g, mx, my, partial);
        int px = hex.getX() + hex.getWidth() + 6;
        g.fill(px, hex.getY(), x + pickerW() - PAD, hex.getY() + 14, 0xFFFFFFFF);
        g.fill(px + 1, hex.getY() + 1, x + pickerW() - PAD - 1, hex.getY() + 13, 0xFF000000 | customRgb());
        g.pose().popPose();
    }

    private boolean inPicker(double mx, double my) {
        return picker && mx >= pickerX && my >= pickerY && mx < pickerX + pickerW() && my < pickerY + pickerH();
    }

    /** Applies a click or drag inside the picker; returns which part (1 square, 2 hue bar) took it, or 0. */
    private int pickAt(double mx, double my, int part) {
        int sx = pickerX + PAD, sy = pickerY + PAD, hx = sx + SV + PAD;
        if (part == 1 || part == 0 && mx >= sx && mx < sx + SV && my >= sy && my < sy + SV) {
            sat = Mth.clamp((float) (mx - sx) / (SV - 1), 0, 1);
            val = 1 - Mth.clamp((float) (my - sy) / (SV - 1), 0, 1);
            part = 1;
        } else if (part == 2 || part == 0 && mx >= hx - 2 && mx < hx + HUE_W + 2 && my >= sy && my < sy + SV) {
            hue = Mth.clamp((float) (my - sy) / SV, 0, 0.999f);
            part = 2;
        } else return 0;
        color = CUSTOM;
        hex.setValue(String.format("%06X", customRgb()));
        return part;
    }

    private boolean inCanvas(double mx, double my) {
        return mx >= left && my >= top && mx < left + spanW && my < top + spanH;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (inPicker(mx, my)) {
            if (hex.mouseClicked(mx, my, button)) {
                setFocused(hex);
                return true;
            }
            hex.setFocused(false);
            pickerDrag = pickAt(mx, my, 0);
            return true;
        }
        if (super.mouseClicked(mx, my, button)) return true;
        int tt = toolsTop();
        for (int i = 0; i <= CUSTOM; i++) {
            int x = paletteX + (i % 2) * CELL, y = tt + (i / 2) * CELL;
            if (mx >= x && my >= y && mx < x + CELL - 2 && my < y + CELL - 2) {
                if (i == CUSTOM) togglePicker(!(picker && color == CUSTOM));
                else togglePicker(false);
                color = i;
                return true;
            }
        }
        if (picker) {
            togglePicker(false);
            return true;
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
            stroke = new StrokeSession(board, button == 1 ? 0 : ink(), brush);
            stroke.add(pointAt(mx, my));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (pickerDrag != 0) {
            pickAt(mx, my, pickerDrag);
            return true;
        }
        if (stroke != null) {
            stroke.add(pointAt(mx, my));
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (pickerDrag != 0) {
            pickerDrag = 0;
            return true;
        }
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
        if (picker && hex.isFocused() && key != GLFW.GLFW_KEY_ESCAPE) return hex.keyPressed(key, scan, mods) || true;
        if (picker && key == GLFW.GLFW_KEY_ESCAPE) {
            togglePicker(false);
            return true;
        }
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
        Map<BlockPos, Drawing> before = new HashMap<>();
        for (int row = 0; row < board.height(); row++)
            for (int col = 0; col < board.width(); col++) {
                WhiteboardBlockEntity be = board.at(minecraft.level, col, row);
                if (be == null) continue;
                Drawing snap = be.snapshot();
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
