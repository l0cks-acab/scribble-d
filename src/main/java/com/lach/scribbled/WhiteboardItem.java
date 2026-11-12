package com.lach.scribbled;

import java.util.function.Consumer;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

public class WhiteboardItem extends BlockItem {
    public WhiteboardItem(Block block, Properties props) {
        super(block, props);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        if (stack.has(Scribbled.DRAWING.get()))
            tooltip.accept(Component.translatable("tooltip.scribbled.drawn").withStyle(ChatFormatting.GRAY));
    }
}
