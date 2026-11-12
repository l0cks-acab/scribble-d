package com.lach.scribbled.client;

import com.lach.scribbled.Canvas;
import com.lach.scribbled.MarkerItem;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Tints the marker's tip and cap by its {@code scribbled:marker_color} component. */
public record MarkerTint() implements ItemTintSource {
    public static final MapCodec<MarkerTint> MAP_CODEC = MapCodec.unit(new MarkerTint());

    @Override
    public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner) {
        return 0xFF000000 | Canvas.rgb(MarkerItem.color(stack));
    }

    @Override
    public MapCodec<MarkerTint> type() {
        return MAP_CODEC;
    }
}
