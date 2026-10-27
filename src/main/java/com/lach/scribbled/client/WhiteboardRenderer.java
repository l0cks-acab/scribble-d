package com.lach.scribbled.client;

import com.lach.scribbled.Scribbled;
import com.lach.scribbled.WhiteboardBlock;
import com.lach.scribbled.WhiteboardBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/** Draws the canvas over the whole front face, plus a frame strip on every edge not merged with another board. */
public class WhiteboardRenderer implements BlockEntityRenderer<WhiteboardBlockEntity> {
    private static final ResourceLocation FRAME = Scribbled.id("textures/block/whiteboard_frame.png");
    private static final float SURFACE = -0.5f + 2 / 16f + 0.002f;
    private static final float F = 1 / 16f;

    public WhiteboardRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(WhiteboardBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        int l = be.isGlowing() ? LightTexture.FULL_BRIGHT : light;
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-state.getValue(WhiteboardBlock.FACING).toYRot()));
        PoseStack.Pose p = pose.last();
        quad(buffers.getBuffer(RenderType.entitySolid(BoardTextures.get(be))), p, -0.5f, -0.5f, 0.5f, 0.5f, SURFACE, true, l);

        VertexConsumer frame = buffers.getBuffer(RenderType.entitySolid(FRAME));
        float z = SURFACE + 0.001f;
        if (!state.getValue(WhiteboardBlock.UP)) quad(frame, p, -0.5f, 0.5f - F, 0.5f, 0.5f, z, false, light);
        if (!state.getValue(WhiteboardBlock.DOWN)) quad(frame, p, -0.5f, -0.5f, 0.5f, -0.5f + F, z, false, light);
        if (!state.getValue(WhiteboardBlock.LEFT)) quad(frame, p, -0.5f, -0.5f, -0.5f + F, 0.5f, z, false, light);
        if (!state.getValue(WhiteboardBlock.RIGHT)) quad(frame, p, 0.5f - F, -0.5f, 0.5f, 0.5f, z, false, light);
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
