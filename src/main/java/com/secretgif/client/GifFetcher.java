package com.secretgif.client;

import com.secretgif.common.GifEntry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Загружает список GIF-ок с secretnn.pythonanywhere.com
 */
public class GifFetcher {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final String BASE_URL = "https://secretnn.pythonanywhere.com";

    /**
     * Асинхронно загружает список GIF-ок и вызывает callback с результатом
     */
    public static void fetchGifList(Consumer<List<GifEntry>> onSuccess, Consumer<Exception> onError) {
        CompletableFuture.supplyAsync(() -> {
            try {
                return parseGifList();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).thenAccept(onSuccess).exceptionally(ex -> {
            onError.accept((Exception) ex.getCause());
            return null;
        });
    }

    private static List<GifEntry> parseGifList() throws Exception {
        // LinkedHashMap чтобы сохранить порядок и избежать дублей по ID
        java.util.LinkedHashMap<Integer, GifEntry> gifMap = new java.util.LinkedHashMap<>();

        URL url = new URL(BASE_URL + "/");
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append("\n");
        }

        String html = sb.toString();

        // Парсим паттерн: ### Название\n\n[/get_gif/ID]
        Pattern p = Pattern.compile("###\\s+(.+?)\\n+\\[/get_gif/(\\d+)\\]");
        Matcher m = p.matcher(html);

        while (m.find()) {
            String name = m.group(1).trim();
            int id = Integer.parseInt(m.group(2));
            gifMap.putIfAbsent(id, new GifEntry(id, name, BASE_URL + "/get_gif/" + id));
        }

        // fallback: просто ищем /get_gif/N ссылки если основной парсер ничего не нашёл
        if (gifMap.isEmpty()) {
            Pattern p2 = Pattern.compile("/get_gif/(\\d+)");
            Matcher m2 = p2.matcher(html);
            while (m2.find()) {
                int id = Integer.parseInt(m2.group(1));
                gifMap.putIfAbsent(id, new GifEntry(id, "GIF #" + id, BASE_URL + "/get_gif/" + id));
            }
        }

        return new ArrayList<>(gifMap.values());
    }
}