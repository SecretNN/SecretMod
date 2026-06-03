package com.secretgif.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.secretgif.client.GifLoader;
import com.secretgif.common.ActiveGifData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import com.mojang.blaze3d.vertex.*;
import org.joml.Matrix4f;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Рендерит GIF-ки над головами игроков.
 * Использует RenderLivingEvent.Post — срабатывает для ВСЕХ живых существ,
 * включая локального игрока и других в мультиплеере.
 */
@EventBusSubscriber(modid = "secretgif", value = Dist.CLIENT)
public class GifRenderer {

    // Размер GIF над головой в мировых единицах (1 блок = 1.0)
    private static final float GIF_SIZE = 1.2f;

    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Post event) {
        if (!(event.getEntity() instanceof Player player)) return;

        UUID uuid = player.getUUID();
        ActiveGifData.ActiveGif activeGif = ActiveGifData.ACTIVE_GIFS.get(uuid);
        if (activeGif == null) return;

        // Загружаем анимацию если ещё не загружена
        GifLoader.GifAnimation anim = GifLoader.getAnimation(activeGif.url);
        if (anim == null) {
            GifLoader.loadAsync(activeGif.url);
            return;
        }

        // Проверяем не истекло ли время
        long totalDuration = anim.getTotalDurationMs() * activeGif.repeatCount;
        long elapsed = System.currentTimeMillis() - activeGif.startTimeMs;
        if (elapsed > totalDuration) {
            ActiveGifData.ACTIVE_GIFS.remove(uuid);
            return;
        }

        ResourceLocation frameTex = getFrameForTime(anim, elapsed);
        if (frameTex == null) return;

        var poseStack = event.getPoseStack();
        poseStack.pushPose();

        // Двигаемся к позиции над головой игрока
        // RenderLivingEvent уже в системе координат игрока (0,0,0 = ноги)
        float headHeight = player.isCrouching() ? 1.35f : 1.8f;
        poseStack.translate(0.0, headHeight + 0.85, 0.0);

        // Billboard — всегда смотрит на камеру
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        poseStack.mulPose(camera.rotation());

        float half = GIF_SIZE / 2f;
        // Никакого scale — billboard уже правильно ориентирован
        // UV: без инверсий, стандартные координаты
        Matrix4f matrix = poseStack.last().pose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, frameTex);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        BufferBuilder buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

        // UV (0,0) = верхний левый, Y мира растёт вверх → V инвертирован
        buffer.addVertex(matrix, -half, -half, 0f).setUv(0f, 1f);
        buffer.addVertex(matrix,  half, -half, 0f).setUv(1f, 1f);
        buffer.addVertex(matrix,  half,  half, 0f).setUv(1f, 0f);
        buffer.addVertex(matrix, -half,  half, 0f).setUv(0f, 0f);

        BufferUploader.drawWithShader(buffer.build());

        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        poseStack.popPose();
    }

    private static ResourceLocation getFrameForTime(GifLoader.GifAnimation anim, long elapsedMs) {
        if (anim.frames.isEmpty()) return null;
        long singleRun = anim.getTotalDurationMs();
        if (singleRun == 0) return anim.frames.get(0).texture;
        long timeInCycle = elapsedMs % singleRun;
        long acc = 0;
        for (GifLoader.GifFrame frame : anim.frames) {
            acc += frame.delayMs;
            if (timeInCycle < acc) return frame.texture;
        }
        return anim.frames.get(anim.frames.size() - 1).texture;
    }

    public static void tick() {
        Iterator<Map.Entry<UUID, ActiveGifData.ActiveGif>> it =
                ActiveGifData.ACTIVE_GIFS.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            ActiveGifData.ActiveGif ag = e.getValue();
            GifLoader.GifAnimation anim = GifLoader.getAnimation(ag.url);
            if (anim != null) {
                long total = anim.getTotalDurationMs() * ag.repeatCount;
                if (System.currentTimeMillis() - ag.startTimeMs > total) it.remove();
            }
        }
    }
}