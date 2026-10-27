package com.lach.scribbled;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class MarkerItem extends Item {
    public MarkerItem(Properties props) {
        super(props);
    }

    public static int color(ItemStack stack) {
        return stack.getOrDefault(Scribbled.MARKER_COLOR.get(), 16);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.scribbled.marker.colored",
                Component.translatable("color.minecraft." + DyeColor.byId(color(stack) - 1).getName()));
    }
}
