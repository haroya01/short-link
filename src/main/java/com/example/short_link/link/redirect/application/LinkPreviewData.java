package com.example.short_link.link.redirect.application;

public record LinkPreviewData(
    String title,
    String description,
    String shortUrl,
    String image,
    String originalUrl,
    boolean generatedImage) {}
