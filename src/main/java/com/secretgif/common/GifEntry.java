package com.secretgif.common;

/**
 * Хранит информацию об одной GIF-ке с сервера
 */
public class GifEntry {
    public final int id;
    public final String name;
    public final String url;

    public GifEntry(int id, String name, String url) {
        this.id = id;
        this.name = name;
        this.url = url;
    }
}
