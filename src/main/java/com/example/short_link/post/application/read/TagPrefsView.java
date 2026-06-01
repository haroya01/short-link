package com.example.short_link.post.application.read;

import java.util.List;

/**
 * A user's tag preferences: tags they follow (personal strip) and tags they hide (filtered out).
 */
public record TagPrefsView(List<String> followed, List<String> hidden) {}
