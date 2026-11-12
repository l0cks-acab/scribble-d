package com.lach.scribbled;

import com.lach.scribbled.client.ClientHooks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** A thin board; FACING is the direction its drawing surface looks. Side flags mark same-facing neighbours it merges with. */
public class WhiteboardBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<WhiteboardBlock> CODEC = simpleCodec(WhiteboardBlock::new);
    static BlockBehaviour.Properties properties(BlockBehaviour.Properties p) {
        return p.mapColor(MapColor.QUARTZ).strength(1.0f).sound(SoundType.METAL).noOcclusion();
    }

    public static final BooleanProperty UP = BooleanProperty.create("up");
    public static final BooleanProperty DOWN = BooleanProperty.create("down");
    public static final BooleanProperty LEFT = BooleanProperty.create("left");
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");

    private static final VoxelShape NORTH = box(0, 0, 14, 16, 16, 16);
    private static final VoxelShape SOUTH = box(0, 0, 0, 16, 16, 2);
    private static final VoxelShape EAST = box(0, 0, 0, 2, 16, 16);
    private static final VoxelShape WEST = box(14, 0, 0, 16, 16, 16);

    public WhiteboardBlock(BlockBehaviour.Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(UP, false).setValue(DOWN, false).setValue(LEFT, false).setValue(RIGHT, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, UP, DOWN, LEFT, RIGHT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction face = ctx.getClickedFace();
        Direction facing = face.getAxis().isHorizontal() ? face : ctx.getHorizontalDirection().getOpposite();
        // clicking the edge of an existing board extends it instead of turning the corner
        BlockState against = ctx.getLevel().getBlockState(ctx.getClickedPos().relative(face.getOpposite()));
        if (against.getBlock() instanceof WhiteboardBlock && against.getValue(FACING).getAxis() != face.getAxis())
            facing = against.getValue(FACING);
        return connect(defaultBlockState().setValue(FACING, facing), ctx.getLevel(), ctx.getClickedPos());
    }

    private BlockState connect(BlockState state, BlockGetter level, BlockPos pos) {
        Direction facing = state.getValue(FACING), right = Board.right(facing);
        return state.setValue(UP, Board.isBoard(level.getBlockState(pos.above()), facing))
                .setValue(DOWN, Board.isBoard(level.getBlockState(pos.below()), facing))
                .setValue(LEFT, Board.isBoard(level.getBlockState(pos.relative(right.getOpposite())), facing))
                .setValue(RIGHT, Board.isBoard(level.getBlockState(pos.relative(right)), facing));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        // covers states set without placement context (commands, structures)
        BlockState connected = connect(state, level, pos);
        if (connected != state) level.setBlock(pos, connected, Block.UPDATE_CLIENTS);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction dir,
                                     BlockPos neighborPos, BlockState neighbor, RandomSource random) {
        return connect(state, level, pos);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH;
            case EAST -> EAST;
            case WEST -> WEST;
            default -> NORTH;
        };
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (stack.is(Items.GLOW_INK_SAC) || stack.is(Items.INK_SAC)) {
            if (!(level.getBlockEntity(pos) instanceof WhiteboardBlockEntity be)) return InteractionResult.PASS;
            boolean glow = stack.is(Items.GLOW_INK_SAC);
            if (be.isGlowing() == glow) return InteractionResult.PASS;
            if (!level.isClientSide()) be.setGlowing(glow);
            level.playSound(player, pos, glow ? SoundEvents.GLOW_INK_SAC_USE : SoundEvents.INK_SAC_USE, SoundSource.BLOCKS, 1f, 1f);
            stack.consume(1, player);
            return InteractionResult.SUCCESS;
        }
        if (stack.is(Scribbled.MARKER.get()) || stack.is(Scribbled.ERASER.get()))
            return InteractionResult.CONSUME; // drawn client-side while use is held, see MarkerDrawing
        return stack.getItem() instanceof BlockItem ? InteractionResult.PASS
                : InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) ClientHooks.openBoard(pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WhiteboardBlockEntity(pos, state);
    }
}
