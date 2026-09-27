package com.example.short_link.post.domain;

public record PostLinkClick(String shortCode, String destinationUrl, long clicks) {}
