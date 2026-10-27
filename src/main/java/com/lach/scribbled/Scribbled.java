package com.lach.scribbled;

import com.lach.scribbled.net.ClearPayload;
import com.lach.scribbled.net.PaintPayload;
import com.lach.scribbled.net.RestorePayload;
import com.mojang.serialization.Codec;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(Scribbled.MODID)
public class Scribbled {
    public static final String MODID = "scribbled";
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Drawing>> DRAWING = COMPONENTS.registerComponentType("drawing",
            b -> b.persistent(Drawing.CODEC).networkSynchronized(Drawing.STREAM_CODEC));
    /** Palette index (1-16) a marker draws with. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> MARKER_COLOR = COMPONENTS.registerComponentType("marker_color",
            b -> b.persistent(Codec.intRange(1, Canvas.COLORS - 1)).networkSynchronized(ByteBufCodecs.VAR_INT));

    public static final DeferredBlock<WhiteboardBlock> BOARD = BLOCKS.registerBlock("whiteboard", WhiteboardBlock::new, WhiteboardBlock.PROPERTIES);
    public static final DeferredItem<WhiteboardItem> BOARD_ITEM = ITEMS.registerItem("whiteboard", p -> new WhiteboardItem(BOARD.get(), p), new Item.Properties());
    public static final DeferredItem<MarkerItem> MARKER = ITEMS.registerItem("marker", MarkerItem::new, new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> ERASER = ITEMS.registerItem("eraser", Item::new, new Item.Properties().stacksTo(1));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WhiteboardBlockEntity>> BOARD_BE = BLOCK_ENTITIES.register("whiteboard",
            () -> BlockEntityType.Builder.of(WhiteboardBlockEntity::new, BOARD.get()).build(null));

    public Scribbled(IEventBus modBus, ModContainer container) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        COMPONENTS.register(modBus);
        modBus.addListener(Scribbled::registerPayloads);
        modBus.addListener(Scribbled::creativeTabs);
        LOGGER.info("{} loaded", MODID);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    private static void creativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) event.accept(BOARD_ITEM.get());
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            for (int c = 1; c < Canvas.COLORS; c++) event.accept(marker(c));
            event.accept(ERASER.get());
        }
    }

    public static ItemStack marker(int color) {
        ItemStack s = new ItemStack(MARKER.get());
        s.set(MARKER_COLOR.get(), color);
        return s;
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("2");
        r.playToServer(PaintPayload.TYPE, PaintPayload.STREAM_CODEC, (p, ctx) -> {
            Board b = p.board();
            if (!reachable(ctx.player(), b)) return;
            for (int row = 0; row < b.height(); row++)
                for (int col = 0; col < b.width(); col++) {
                    WhiteboardBlockEntity be = b.at(ctx.player().level(), col, row);
                    if (be != null && be.paint(p.ink(), p.size(), p.points(), col * Canvas.SIZE, row * Canvas.SIZE)) be.sync();
                }
        });
        r.playToServer(ClearPayload.TYPE, ClearPayload.STREAM_CODEC, (p, ctx) -> {
            Board b = p.board();
            if (!reachable(ctx.player(), b)) return;
            for (int row = 0; row < b.height(); row++)
                for (int col = 0; col < b.width(); col++) {
                    WhiteboardBlockEntity be = b.at(ctx.player().level(), col, row);
                    if (be != null && be.clear()) be.sync();
                }
        });
        r.playToServer(RestorePayload.TYPE, RestorePayload.STREAM_CODEC, (p, ctx) -> {
            BlockPos pos = p.pos();
            Board one = Board.find(ctx.player().level(), pos);
            if (one == null || !reachable(ctx.player(), new Board(pos, one.facing(), 1, 1))) return;
            if (ctx.player().level().getBlockEntity(pos) instanceof WhiteboardBlockEntity be && be.restore(p.drawing())) be.sync();
        });
    }

    private static boolean reachable(Player player, Board board) {
        if (!(player instanceof ServerPlayer sp) || !sp.mayBuild() || !board.valid()) return false;
        if (!sp.level().isLoaded(board.anchor()) || !sp.level().isLoaded(board.pos(board.width() - 1, board.height() - 1))) return false;
        double reach = 8 + Math.max(board.width(), board.height());
        return sp.distanceToSqr(board.center()) <= reach * reach;
    }
}
