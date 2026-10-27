package com.example.short_link.note.domain;

import java.util.List;

// history is the notes per day over the window, oldest first.
public record TrendingLink(
    String url,
    String title,
    String description,
    String imageUrl,
    long accounts,
    long uses,
    List<Long> history) {}
