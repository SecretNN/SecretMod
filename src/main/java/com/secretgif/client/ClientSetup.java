package com.secretgif.client;

import com.secretgif.client.gui.GifPickerScreen;
import com.secretgif.client.render.GifRenderer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Клиентская инициализация:
 * - регистрация горячей клавиши G
 * - тик для обработки нажатий
 */
@EventBusSubscriber(modid = "secretgif", value = Dist.CLIENT)
public class ClientSetup {

    public static KeyMapping OPEN_GIF_KEY;

    public static void init() {
        // Вызывается из modEventBus — здесь можем делать дополнительные init
    }

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        OPEN_GIF_KEY = new KeyMapping(
                "key.secretgif.open",  // lang key
                GLFW.GLFW_KEY_G,       // G
                "key.categories.secretgif"
        );
        event.register(OPEN_GIF_KEY);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();

        // Если нажата G и нет открытого экрана
        while (OPEN_GIF_KEY != null && OPEN_GIF_KEY.consumeClick()) {
            if (mc.screen == null && mc.player != null) {
                mc.setScreen(new GifPickerScreen());
            }
        }

        // Очищаем устаревшие гифки
        GifRenderer.tick();
    }
}