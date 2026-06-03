package com.secretgif.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;

/**
 * Загружает GIF-ки из интернета, разбирает на кадры,
 * регистрирует их как текстуры Minecraft.
 */
public class GifLoader {

    private static final Logger LOGGER = LogManager.getLogger();

    // url → анимация. Максимум MAX_CACHE_SIZE гифок, потом вычищаем старые
    private static final int MAX_CACHE_SIZE = 20;
    private static final Map<String, GifAnimation> CACHE = java.util.Collections.synchronizedMap(
            new java.util.LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, GifAnimation> eldest) {
                    return size() > MAX_CACHE_SIZE;
                }
            }
    );
    private static final Set<String> LOADING = ConcurrentHashMap.newKeySet();
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "SecretGif-Loader");
        t.setDaemon(true);
        return t;
    });

    public static GifAnimation getAnimation(String url) {
        return CACHE.get(url);
    }

    public static boolean isLoading(String url) {
        return LOADING.contains(url);
    }

    public static void loadAsync(String url) {
        if (CACHE.containsKey(url) || LOADING.contains(url)) return;
        LOADING.add(url);

        EXECUTOR.submit(() -> {
            try {
                List<GifFrame> frames = downloadAndParse(url);
                if (!frames.isEmpty()) {
                    // Регистрируем текстуры в главном потоке Minecraft
                    Minecraft.getInstance().execute(() -> {
                        List<GifFrame> registeredFrames = new ArrayList<>();
                        for (int i = 0; i < frames.size(); i++) {
                            GifFrame f = frames.get(i);
                            String texName = "secretgif_" + Math.abs(url.hashCode()) + "_" + i;
                            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath("secretgif", texName);
                            DynamicTexture tex = new DynamicTexture(f.image);
                            Minecraft.getInstance().getTextureManager().register(loc, tex);
                            registeredFrames.add(new GifFrame(f.image, f.delayMs, loc));
                        }
                        CACHE.put(url, new GifAnimation(registeredFrames));
                        LOADING.remove(url);
                    });
                } else {
                    LOADING.remove(url);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to load GIF: {}", url, e);
                LOADING.remove(url);
            }
        });
    }

    private static List<GifFrame> downloadAndParse(String url) throws Exception {
        List<GifFrame> frames = new ArrayList<>();

        try (InputStream is = new URL(url).openStream()) {
            ImageInputStream iis = ImageIO.createImageInputStream(is);
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
            if (!readers.hasNext()) return frames;

            ImageReader reader = readers.next();
            reader.setInput(iis);

            int frameCount = reader.getNumImages(true);
            for (int i = 0; i < frameCount; i++) {
                BufferedImage bi = reader.read(i);
                NativeImage ni = convertToNativeImage(bi);

                // Получаем задержку кадра из метаданных GIF
                int delay = 100; // дефолт 100мс
                try {
                    javax.imageio.metadata.IIOMetadata meta = reader.getImageMetadata(i);
                    String metaFormat = meta.getNativeMetadataFormatName();
                    org.w3c.dom.Node root = meta.getAsTree(metaFormat);
                    delay = extractGifDelay(root);
                } catch (Exception ignored) {}

                frames.add(new GifFrame(ni, delay, null));
            }
            reader.dispose();
        }

        return frames;
    }

    private static int extractGifDelay(org.w3c.dom.Node node) {
        // Ищем GraphicControlExtension → delayTime
        if (node.getNodeName().equals("GraphicControlExtension")) {
            org.w3c.dom.NamedNodeMap attrs = node.getAttributes();
            if (attrs != null) {
                org.w3c.dom.Node delayNode = attrs.getNamedItem("delayTime");
                if (delayNode != null) {
                    return Integer.parseInt(delayNode.getNodeValue()) * 10; // сотые секунды → мс
                }
            }
        }
        org.w3c.dom.NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            int d = extractGifDelay(children.item(i));
            if (d > 0) return d;
        }
        return 100;
    }

    private static NativeImage convertToNativeImage(BufferedImage bi) {
        int w = bi.getWidth();
        int h = bi.getHeight();
        NativeImage ni = new NativeImage(NativeImage.Format.RGBA, w, h, false);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = bi.getRGB(x, y);
                // Java BufferedImage: ARGB
                // NativeImage.setPixelRGBA принимает: упакованный int в порядке RGBA (R в байте 0)
                int a = (argb >> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >>  8) & 0xFF;
                int b =  argb        & 0xFF;
                // NativeImage хранит байты как R, G, B, A
                int rgba = (a << 24) | (b << 16) | (g << 8) | r;
                ni.setPixelRGBA(x, y, rgba);
            }
        }
        return ni;
    }

    public static class GifAnimation {
        public final List<GifFrame> frames;
        private int currentFrame = 0;
        private long lastFrameTime = 0;

        public GifAnimation(List<GifFrame> frames) {
            this.frames = frames;
        }

        /** Возвращает ResourceLocation текущего кадра с учётом времени */
        public ResourceLocation getCurrentFrameTexture() {
            if (frames.isEmpty()) return null;
            long now = System.currentTimeMillis();
            if (now - lastFrameTime > frames.get(currentFrame).delayMs) {
                currentFrame = (currentFrame + 1) % frames.size();
                lastFrameTime = now;
            }
            return frames.get(currentFrame).texture;
        }

        /** Возвращает текущий индекс кадра (0-based) */
        public int getCurrentFrameIndex() {
            return currentFrame;
        }

        /** Полная длительность одного прогона анимации в мс */
        public long getTotalDurationMs() {
            return frames.stream().mapToLong(f -> f.delayMs).sum();
        }
    }

    public static class GifFrame {
        public final NativeImage image;
        public final int delayMs;
        public final ResourceLocation texture; // null до регистрации

        public GifFrame(NativeImage image, int delayMs, ResourceLocation texture) {
            this.image = image;
            this.delayMs = delayMs;
            this.texture = texture;
        }
    }
}