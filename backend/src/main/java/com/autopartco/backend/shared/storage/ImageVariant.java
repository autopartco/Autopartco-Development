package com.autopartco.backend.shared.storage;

/** Tamaños derivados de una imagen de producto (recorte tipo cover, formato WebP). */
public enum ImageVariant {
    THUMB(400, 400),
    CARD(800, 800);

    private final int width;
    private final int height;

    ImageVariant(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }
}
