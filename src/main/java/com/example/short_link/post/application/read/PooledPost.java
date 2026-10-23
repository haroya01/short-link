package com.example.short_link.post.application.read;

import java.time.Instant;
import java.util.List;

public record PooledPost(
    long id,
    long userId,
    String language,
    Instant publishedAt,
    List<String> tags,
    long likes,
    long quotes,
    long highlights) {}
