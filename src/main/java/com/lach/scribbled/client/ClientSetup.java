package com.lach.scribbled.client;

import com.lach.scribbled.Scribbled;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public final class ClientSetup {
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Scribbled.id("main"));
    public static final KeyMapping UNDO = new KeyMapping("key.scribbled.undo", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY);

    private ClientSetup() {}

    @EventBusSubscriber(modid = Scribbled.MODID, value = Dist.CLIENT)
    static final class ModEvents {
        @SubscribeEvent
        static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerBlockEntityRenderer(Scribbled.BOARD_BE.get(), WhiteboardRenderer::new);
        }

        @SubscribeEvent
        static void keys(RegisterKeyMappingsEvent event) {
            event.registerCategory(CATEGORY);
            event.register(UNDO);
        }

        @SubscribeEvent
        static void tintSources(RegisterColorHandlersEvent.ItemTintSources event) {
            event.register(Scribbled.id("marker_color"), MarkerTint.MAP_CODEC);
        }
    }

    @EventBusSubscriber(modid = Scribbled.MODID, value = Dist.CLIENT)
    static final class GameEvents {
        @SubscribeEvent
        static void frame(RenderFrameEvent.Pre event) {
            MarkerDrawing.frame();
        }

        @SubscribeEvent
        static void tick(ClientTickEvent.Post event) {
            MarkerDrawing.tick();
            while (UNDO.consumeClick()) UndoHistory.undo();
        }

        @SubscribeEvent
        static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            MarkerDrawing.end();
            UndoHistory.clear();
            BoardTextures.releaseAll();
        }
    }
}
