package com.secretgif.common;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Хранит активные GIF-ки над головами игроков.
 * Ключ = UUID игрока, значение = данные активной GIF-ки.
 */
public class ActiveGifData {

    public static final Map<UUID, ActiveGif> ACTIVE_GIFS = new ConcurrentHashMap<>();

    public static class ActiveGif {
        public final String url;
        public final long startTimeMs;
        public final int repeatCount; // сколько раз повторить анимацию

        public ActiveGif(String url, long startTimeMs, int repeatCount) {
            this.url = url;
            this.startTimeMs = startTimeMs;
            this.repeatCount = repeatCount;
        }
    }
}
