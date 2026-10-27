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
    private boolean glowing;
    /** Bumped on every pixel change so client textures know to re-upload. */
    private int version;

    public WhiteboardBlockEntity(BlockPos pos, BlockState state) {
        super(Scribbled.BOARD_BE.get(), pos, state);
    }

    public byte[] pixels() {
        return pixels;
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

    public boolean paint(int color, int size, List<Integer> points, int ox, int oy) {
        if (!Canvas.stroke(pixels, color, size, points, ox, oy)) return false;
        version++;
        return true;
    }

    public boolean clear() {
        if (isBlank()) return false;
        Arrays.fill(pixels, (byte) 0);
        version++;
        return true;
    }

    public boolean restore(byte[] snapshot) {
        if (snapshot.length != Canvas.AREA || Arrays.equals(pixels, snapshot)) return false;
        pixels = snapshot.clone();
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
        tag.putBoolean("glowing", glowing);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("pixels")) pixels = Arrays.copyOf(tag.getByteArray("pixels"), Canvas.AREA);
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
        if (!isBlank()) components.set(Scribbled.DRAWING.get(), new Drawing(pixels));
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput input) {
        super.applyImplicitComponents(input);
        Drawing d = input.get(Scribbled.DRAWING.get());
        if (d != null) {
            pixels = d.copyPixels();
            version++;
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void removeComponentsFromTag(CompoundTag tag) {
        tag.remove("pixels");
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && level.isClientSide) ClientHooks.release(this);
    }
}
