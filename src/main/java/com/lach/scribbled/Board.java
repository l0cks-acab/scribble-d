package com.lach.scribbled;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import io.netty.buffer.ByteBuf;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A rectangle of whiteboard blocks sharing one facing. Columns run left to right and rows top to bottom as seen
 * from the front; the anchor is the top-left block. Cells may be empty when the connected boards aren't rectangular.
 */
public record Board(BlockPos anchor, Direction facing, int width, int height) {
    public static final int MAX = 8;
    public static final StreamCodec<ByteBuf, Board> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, Board::anchor,
            Direction.STREAM_CODEC, Board::facing,
            ByteBufCodecs.VAR_INT, Board::width,
            ByteBufCodecs.VAR_INT, Board::height,
            Board::new);

    /** The world direction of the viewer's right when looking at a board facing this way. */
    public static Direction right(Direction facing) {
        return facing.getCounterClockWise();
    }

    public boolean valid() {
        return facing.getAxis().isHorizontal() && width >= 1 && height >= 1 && width <= MAX && height <= MAX;
    }

    public int pixelWidth() {
        return width * Canvas.SIZE;
    }

    public int pixelHeight() {
        return height * Canvas.SIZE;
    }

    public BlockPos pos(int col, int row) {
        return anchor.relative(right(facing), col).below(row);
    }

    public int col(BlockPos pos) {
        Direction r = right(facing);
        return (pos.getX() - anchor.getX()) * r.getStepX() + (pos.getZ() - anchor.getZ()) * r.getStepZ();
    }

    public int row(BlockPos pos) {
        return anchor.getY() - pos.getY();
    }

    public boolean contains(BlockPos pos) {
        int c = col(pos), r = row(pos);
        return c >= 0 && r >= 0 && c < width && r < height && pos(c, r).equals(pos);
    }

    public Vec3 center() {
        Direction r = right(facing);
        return Vec3.atCenterOf(anchor).add(r.getStepX() * (width - 1) / 2.0, -(height - 1) / 2.0, r.getStepZ() * (width - 1) / 2.0);
    }

    @Nullable
    public WhiteboardBlockEntity at(BlockGetter level, int col, int row) {
        BlockPos p = pos(col, row);
        return isBoard(level.getBlockState(p), facing) && level.getBlockEntity(p) instanceof WhiteboardBlockEntity be ? be : null;
    }

    /** Board-global pixel under a point on the front face of the given block. */
    public int pixelAt(BlockPos pos, Vec3 hit) {
        Direction r = right(facing);
        double rx = hit.x - pos.getX(), rz = hit.z - pos.getZ();
        double u = r.getStepX() != 0 ? (r.getStepX() > 0 ? rx : 1 - rx) : (r.getStepZ() > 0 ? rz : 1 - rz);
        double v = 1 - (hit.y - pos.getY());
        int x = Math.min(Canvas.SIZE - 1, Math.max(0, (int) Math.floor(u * Canvas.SIZE)));
        int y = Math.min(Canvas.SIZE - 1, Math.max(0, (int) Math.floor(v * Canvas.SIZE)));
        return Canvas.pack(col(pos) * Canvas.SIZE + x, row(pos) * Canvas.SIZE + y);
    }

    public static boolean isBoard(BlockState state, Direction facing) {
        return state.getBlock() instanceof WhiteboardBlock && state.getValue(WhiteboardBlock.FACING) == facing;
    }

    /** Flood-fills connected boards around a block, clipped to a MAX x MAX window containing it. */
    @Nullable
    public static Board find(BlockGetter level, BlockPos start) {
        BlockState state = level.getBlockState(start);
        if (!(state.getBlock() instanceof WhiteboardBlock)) return null;
        Direction facing = state.getValue(WhiteboardBlock.FACING);
        Direction right = right(facing);
        Direction[] steps = {right, right.getOpposite(), Direction.UP, Direction.DOWN};
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        Board probe = new Board(start, facing, 1, 1);
        int minC = 0, maxC = 0, minR = 0, maxR = 0;
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            int c = probe.col(p), r = probe.row(p);
            minC = Math.min(minC, c);
            maxC = Math.max(maxC, c);
            minR = Math.min(minR, r);
            maxR = Math.max(maxR, r);
            for (Direction d : steps) {
                BlockPos n = p.relative(d);
                int nc = probe.col(n), nr = probe.row(n);
                if (Math.abs(nc) >= MAX || Math.abs(nr) >= MAX || seen.contains(n) || !isBoard(level.getBlockState(n), facing)) continue;
                seen.add(n);
                queue.add(n);
            }
        }
        // clip to a window that still contains the start block
        if (maxC - minC >= MAX) {
            minC = Math.max(minC, Math.min(0, maxC - MAX + 1));
            maxC = minC + MAX - 1;
        }
        if (maxR - minR >= MAX) {
            minR = Math.max(minR, Math.min(0, maxR - MAX + 1));
            maxR = minR + MAX - 1;
        }
        return new Board(probe.pos(minC, minR), facing, maxC - minC + 1, maxR - minR + 1);
    }
}
