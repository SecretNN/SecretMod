package com.secretgif.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.secretgif.client.GifFetcher;
import com.secretgif.client.GifLoader;
import com.secretgif.common.GifEntry;
import com.secretgif.network.ShowGifPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Экран выбора GIF-ки (открывается кнопкой G).
 * Показывает размытый фон, сетку GIF-ок, пагинацию.
 */
public class GifPickerScreen extends Screen {

    // Сколько GIF-ок в ряду и строк
    private static final int COLS = 4;
    private static final int ROWS = 2;
    private static final int GIFS_PER_PAGE = COLS * ROWS;

    private static final int CELL_SIZE = 80; // px
    private static final int CELL_PADDING = 8;
    private static final int PREVIEW_SIZE = 64;

    private final List<GifEntry> gifList = new ArrayList<>();
    private boolean loading = true;
    private String errorMsg = null;
    private int currentPage = 0;

    // Превью-анимации для ячеек (загружаются на лету)
    private final List<GifLoader.GifAnimation> previewAnimations = new ArrayList<>();

    public GifPickerScreen() {
        super(Component.literal("Выбери GIF-ку"));
    }

    @Override
    protected void init() {
        super.init();
        // Загружаем список GIF-ок с сайта
        GifFetcher.fetchGifList(
                list -> {
                    gifList.clear();
                    gifList.addAll(list);
                    loading = false;
                    // Предзагружаем превью первой страницы
                    preloadPagePreviews(0);
                },
                err -> {
                    loading = false;
                    errorMsg = "Ошибка загрузки: " + err.getMessage();
                }
        );
    }

    private void preloadPagePreviews(int page) {
        int start = page * GIFS_PER_PAGE;
        int end = Math.min(start + GIFS_PER_PAGE, gifList.size());
        for (int i = start; i < end; i++) {
            GifLoader.loadAsync(gifList.get(i).url);
        }
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        // --- 1. Размытый полупрозрачный фон ---
        renderBlurBackground(gfx);

        int sw = this.width;
        int sh = this.height;

        // --- 2. Заголовок ---
        gfx.drawCenteredString(this.font, "✦ Выбери GIF-ку ✦", sw / 2, 14, 0xFFFFDD55);

        if (loading) {
            gfx.drawCenteredString(this.font, "Загрузка...", sw / 2, sh / 2, 0xFFAAAAAA);
            return;
        }

        if (errorMsg != null) {
            gfx.drawCenteredString(this.font, errorMsg, sw / 2, sh / 2, 0xFFFF5555);
            return;
        }

        if (gifList.isEmpty()) {
            gfx.drawCenteredString(this.font, "GIF-ок нет :(", sw / 2, sh / 2, 0xFFAAAAAA);
            return;
        }

        // --- 3. Сетка GIF-ок ---
        int totalPages = (int) Math.ceil(gifList.size() / (double) GIFS_PER_PAGE);
        int start = currentPage * GIFS_PER_PAGE;
        int end = Math.min(start + GIFS_PER_PAGE, gifList.size());

        int gridWidth = COLS * (CELL_SIZE + CELL_PADDING) - CELL_PADDING;
        int gridStartX = (sw - gridWidth) / 2;
        int gridStartY = 36;

        for (int i = start; i < end; i++) {
            int idx = i - start;
            int col = idx % COLS;
            int row = idx / COLS;

            int cellX = gridStartX + col * (CELL_SIZE + CELL_PADDING);
            int cellY = gridStartY + row * (CELL_SIZE + CELL_PADDING);

            renderGifCell(gfx, gifList.get(i), cellX, cellY, mouseX, mouseY);
        }

        // --- 4. Пагинация ---
        if (totalPages > 1) {
            int btnY = gridStartY + ROWS * (CELL_SIZE + CELL_PADDING) + 6;

            // Кнопка "назад"
            if (currentPage > 0) {
                boolean hover = mouseX >= sw / 2 - 80 && mouseX <= sw / 2 - 10
                        && mouseY >= btnY && mouseY <= btnY + 16;
                gfx.fill(sw / 2 - 80, btnY, sw / 2 - 10, btnY + 16,
                        hover ? 0xCC4466AA : 0xCC223355);
                gfx.drawCenteredString(this.font, "◀ Назад", sw / 2 - 45, btnY + 4, 0xFFFFFFFF);
            }

            // Номер страницы
            gfx.drawCenteredString(this.font,
                    (currentPage + 1) + " / " + totalPages, sw / 2, btnY + 4, 0xFFCCCCCC);

            // Кнопка "вперёд"
            if (currentPage < totalPages - 1) {
                boolean hover = mouseX >= sw / 2 + 10 && mouseX <= sw / 2 + 80
                        && mouseY >= btnY && mouseY <= btnY + 16;
                gfx.fill(sw / 2 + 10, btnY, sw / 2 + 80, btnY + 16,
                        hover ? 0xCC4466AA : 0xCC223355);
                gfx.drawCenteredString(this.font, "Вперёд ▶", sw / 2 + 45, btnY + 4, 0xFFFFFFFF);
            }
        }

        // --- 5. Подсказка закрытия ---
        gfx.drawCenteredString(this.font, "[ESC] — закрыть", sw / 2, sh - 14, 0xFF888888);
    }

    private void renderBlurBackground(GuiGraphics gfx) {
        int sw = this.width;
        int sh = this.height;

        // Несколько слоёв полупрозрачного чёрного = имитация размытия
        gfx.fill(0, 0, sw, sh, 0xB0000000);
        gfx.fill(2, 2, sw - 2, sh - 2, 0x18FFFFFF);
    }

    private void renderGifCell(GuiGraphics gfx, GifEntry entry, int x, int y,
                               int mouseX, int mouseY) {
        boolean hover = mouseX >= x && mouseX <= x + CELL_SIZE
                && mouseY >= y && mouseY <= y + CELL_SIZE;

        // Фон ячейки
        gfx.fill(x, y, x + CELL_SIZE, y + CELL_SIZE,
                hover ? 0xCC4488CC : 0xCC1A2A3A);
        gfx.fill(x + 1, y + 1, x + CELL_SIZE - 1, y + CELL_SIZE - 1,
                hover ? 0xAA2255AA : 0xAA0D1A27);

        // Рендер GIF-превью
        GifLoader.GifAnimation anim = GifLoader.getAnimation(entry.url);
        int imgX = x + (CELL_SIZE - PREVIEW_SIZE) / 2;
        int imgY = y + 2;

        if (anim != null && !anim.frames.isEmpty()) {
            ResourceLocation frameTex = anim.getCurrentFrameTexture();
            if (frameTex != null) {
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                gfx.blit(frameTex, imgX, imgY, 0, 0, PREVIEW_SIZE, PREVIEW_SIZE,
                        PREVIEW_SIZE, PREVIEW_SIZE);
            }
        } else {
            // Плейсхолдер пока грузится
            gfx.fill(imgX, imgY, imgX + PREVIEW_SIZE, imgY + PREVIEW_SIZE, 0x55FFFFFF);
            gfx.drawCenteredString(this.font, "...",
                    imgX + PREVIEW_SIZE / 2, imgY + PREVIEW_SIZE / 2 - 4, 0xFFAAAAAA);
        }

        // Название гифки
        String name = entry.name.length() > 9 ? entry.name.substring(0, 9) + ".." : entry.name;
        gfx.drawCenteredString(this.font, name,
                x + CELL_SIZE / 2, y + CELL_SIZE - 11, hover ? 0xFFFFDD55 : 0xFFCCCCCC);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        int sw = this.width;

        // Клик по ячейке GIF
        if (!gifList.isEmpty()) {
            int gridWidth = COLS * (CELL_SIZE + CELL_PADDING) - CELL_PADDING;
            int gridStartX = (sw - gridWidth) / 2;
            int gridStartY = 36;

            int start = currentPage * GIFS_PER_PAGE;
            int end = Math.min(start + GIFS_PER_PAGE, gifList.size());

            for (int i = start; i < end; i++) {
                int idx = i - start;
                int col = idx % COLS;
                int row = idx / COLS;
                int cellX = gridStartX + col * (CELL_SIZE + CELL_PADDING);
                int cellY = gridStartY + row * (CELL_SIZE + CELL_PADDING);

                if (mouseX >= cellX && mouseX <= cellX + CELL_SIZE
                        && mouseY >= cellY && mouseY <= cellY + CELL_SIZE) {
                    selectGif(gifList.get(i));
                    return true;
                }
            }

            // Клик по пагинации
            int totalPages = (int) Math.ceil(gifList.size() / (double) GIFS_PER_PAGE);
            if (totalPages > 1) {
                int btnY = gridStartY + ROWS * (CELL_SIZE + CELL_PADDING) + 6;

                if (currentPage > 0 && mouseX >= sw / 2 - 80 && mouseX <= sw / 2 - 10
                        && mouseY >= btnY && mouseY <= btnY + 16) {
                    currentPage--;
                    preloadPagePreviews(currentPage);
                    return true;
                }
                if (currentPage < totalPages - 1 && mouseX >= sw / 2 + 10 && mouseX <= sw / 2 + 80
                        && mouseY >= btnY && mouseY <= btnY + 16) {
                    currentPage++;
                    preloadPagePreviews(currentPage);
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void selectGif(GifEntry entry) {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null) return;

        // Сразу записываем локально - работает и в сингле и в мульти
        com.secretgif.common.ActiveGifData.ACTIVE_GIFS.put(
                player.getUUID(),
                new com.secretgif.common.ActiveGifData.ActiveGif(
                        entry.url, System.currentTimeMillis(), 3)
        );

        // Предзагружаем анимацию
        com.secretgif.client.GifLoader.loadAsync(entry.url);

        // Если есть сервер - рассылаем другим игрокам
        if (mc.getConnection() != null) {
            PacketDistributor.sendToServer(new ShowGifPacket(player.getUUID(), entry.url, 3));
        }

        // Закрываем экран
        this.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false; // Не останавливать игру
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // ESC закрывает
        if (keyCode == 256) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}