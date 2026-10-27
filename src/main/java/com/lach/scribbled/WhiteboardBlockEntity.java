package com.lach.scribbled;

import com.lach.scribbled.client.ClientHooks;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class WhiteboardBlockEntity extends BlockEntity {
    private byte[] pixels = new byte[Canvas.AREA];
    /** RGB of palette indices 17 and up. */
    private int[] palette = new int[0];
    private boolean glowing;
    /** Bumped on every pixel change so client textures know to re-upload. */
    private int version;

    public WhiteboardBlockEntity(BlockPos pos, BlockState state) {
        super(Scribbled.BOARD_BE.get(), pos, state);
    }

    public byte[] pixels() {
        return pixels;
    }

    /** RGB of a pixel's palette index. */
    public int rgb(int index) {
        index &= 0xFF;
        int c = index - Canvas.COLORS;
        return c >= 0 && c < palette.length ? palette[c] : Canvas.rgb(index);
    }

    public int version() {
        return version;
    }

    public boolean isGlowing() {
        return glowing;
    }

    public boolean isBlank() {
        for (byte b : pixels) if (b != 0) return false;
        return true;
    }

    public boolean paint(int ink, int size, List<Integer> points, int ox, int oy) {
        if (!Canvas.validInk(ink) || !Canvas.touches(size, points, ox, oy)) return false;
        if (!Canvas.stroke(pixels, resolve(ink), size, points, ox, oy)) return false;
        version++;
        return true;
    }

    /** Palette index for an ink, adding it to the custom palette (or falling back to the nearest colour) if needed. */
    private int resolve(int ink) {
        if (ink == 0) return 0;
        int rgb = ink & 0xFFFFFF;
        for (int i = 1; i < Canvas.COLORS; i++) if (Canvas.rgb(i) == rgb) return i;
        for (int i = 0; i < palette.length; i++) if (palette[i] == rgb) return Canvas.COLORS + i;
        if (palette.length >= Canvas.MAX_CUSTOM) compact();
        if (palette.length < Canvas.MAX_CUSTOM) {
            palette = Arrays.copyOf(palette, palette.length + 1);
            palette[palette.length - 1] = rgb;
            return Canvas.COLORS + palette.length - 1;
        }
        int best = 1, bestDist = Integer.MAX_VALUE;
        for (int i = 1; i < Canvas.COLORS + palette.length; i++) {
            int c = rgb(i), dr = (c >> 16 & 255) - (rgb >> 16 & 255), dg = (c >> 8 & 255) - (rgb >> 8 & 255), db = (c & 255) - (rgb & 255);
            int dist = dr * dr + dg * dg + db * db;
            if (dist < bestDist) {
                best = i;
                bestDist = dist;
            }
        }
        return best;
    }

    /** Drops custom colours no pixel uses any more and renumbers the rest. */
    private void compact() {
        boolean[] used = new boolean[palette.length];
        for (byte b : pixels) {
            int c = (b & 0xFF) - Canvas.COLORS;
            if (c >= 0 && c < used.length) used[c] = true;
        }
        int[] remap = new int[palette.length];
        int n = 0;
        for (int i = 0; i < palette.length; i++) if (used[i]) {
            remap[i] = n;
            palette[n++] = palette[i];
        }
        palette = Arrays.copyOf(palette, n);
        for (int k = 0; k < pixels.length; k++) {
            int c = (pixels[k] & 0xFF) - Canvas.COLORS;
            if (c >= 0) pixels[k] = (byte) (c < used.length && used[c] ? Canvas.COLORS + remap[c] : 0);
        }
    }

    public boolean clear() {
        if (isBlank() && palette.length == 0) return false;
        Arrays.fill(pixels, (byte) 0);
        palette = new int[0];
        version++;
        return true;
    }

    public Drawing snapshot() {
        return new Drawing(pixels, palette);
    }

    public boolean restore(Drawing snapshot) {
        if (snapshot.equals(snapshot())) return false;
        pixels = snapshot.copyPixels();
        palette = snapshot.copyPalette();
        version++;
        return true;
    }

    public void setGlowing(boolean glow) {
        glowing = glow;
        sync();
    }

    public void sync() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putByteArray("pixels", pixels);
        tag.putIntArray("palette", palette);
        tag.putBoolean("glowing", glowing);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("pixels")) pixels = Arrays.copyOf(tag.getByteArray("pixels"), Canvas.AREA);
        int[] pal = tag.getIntArray("palette");
        palette = Arrays.copyOf(pal, Math.min(pal.length, Canvas.MAX_CUSTOM));
        glowing = tag.getBoolean("glowing");
        version++;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (!isBlank()) components.set(Scribbled.DRAWING.get(), snapshot());
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput input) {
        super.applyImplicitComponents(input);
        Drawing d = input.get(Scribbled.DRAWING.get());
        if (d != null) {
            pixels = d.copyPixels();
            palette = d.copyPalette();
            version++;
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void removeComponentsFromTag(CompoundTag tag) {
        tag.remove("pixels");
        tag.remove("palette");
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && level.isClientSide) ClientHooks.release(this);
    }
}
