package com.lach.scribbled.client;

import com.lach.scribbled.Scribbled;
import com.lach.scribbled.WhiteboardBlock;
import com.lach.scribbled.WhiteboardBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Draws the canvas over the whole front face, plus a frame strip on every edge not merged with another board. */
public class WhiteboardRenderer implements BlockEntityRenderer<WhiteboardBlockEntity, WhiteboardRenderer.State> {
    private static final Identifier FRAME = Scribbled.id("textures/block/whiteboard_frame.png");
    private static final float SURFACE = -0.5f + 2 / 16f + 0.002f;
    private static final float F = 1 / 16f;

    public static class State extends BlockEntityRenderState {
        @Nullable Identifier canvas;
        float yRot;
        boolean glowing, up, down, left, right;
    }

    public WhiteboardRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(WhiteboardBlockEntity be, State state, float partialTicks, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, camera, breakProgress);
        BlockState bs = be.getBlockState();
        if (!(bs.getBlock() instanceof WhiteboardBlock)) {
            state.canvas = null;
            return;
        }
        state.canvas = BoardTextures.get(be);
        state.yRot = bs.getValue(WhiteboardBlock.FACING).toYRot();
        state.glowing = be.isGlowing();
        state.up = bs.getValue(WhiteboardBlock.UP);
        state.down = bs.getValue(WhiteboardBlock.DOWN);
        state.left = bs.getValue(WhiteboardBlock.LEFT);
        state.right = bs.getValue(WhiteboardBlock.RIGHT);
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        if (state.canvas == null) return;
        int light = state.lightCoords;
        int l = state.glowing ? LightCoordsUtil.FULL_BRIGHT : light;
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-state.yRot));
        out.submitCustomGeometry(pose, RenderTypes.entitySolid(state.canvas), (p, vc) -> quad(vc, p, -0.5f, -0.5f, 0.5f, 0.5f, SURFACE, true, l));
        float z = SURFACE + 0.001f;
        out.submitCustomGeometry(pose, RenderTypes.entitySolid(FRAME), (p, vc) -> {
            if (!state.up) quad(vc, p, -0.5f, 0.5f - F, 0.5f, 0.5f, z, false, light);
            if (!state.down) quad(vc, p, -0.5f, -0.5f, 0.5f, -0.5f + F, z, false, light);
            if (!state.left) quad(vc, p, -0.5f, -0.5f, -0.5f + F, 0.5f, z, false, light);
            if (!state.right) quad(vc, p, 0.5f - F, -0.5f, 0.5f, 0.5f, z, false, light);
        });
        pose.popPose();
    }

    /** Front-facing quad from (x0, y0) to (x1, y1); UVs span the whole texture for the canvas, or match position for the frame. */
    private static void quad(VertexConsumer vc, PoseStack.Pose p, float x0, float y0, float x1, float y1, float z, boolean full, int light) {
        float u0 = full ? 0 : x0 + 0.5f, u1 = full ? 1 : x1 + 0.5f;
        float v0 = full ? 1 : 0.5f - y0, v1 = full ? 0 : 0.5f - y1;
        vertex(vc, p, x0, y0, z, u0, v0, light);
        vertex(vc, p, x1, y0, z, u1, v0, light);
        vertex(vc, p, x1, y1, z, u1, v1, light);
        vertex(vc, p, x0, y1, z, u0, v1, light);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float u, float v, int light) {
        vc.addVertex(p, x, y, z).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 0, 1);
    }
}
