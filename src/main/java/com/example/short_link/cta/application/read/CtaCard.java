package com.example.short_link.cta.application.read;

public record CtaCard(
    Long id, String label, String url, String style, String purpose, boolean deleted) {}
