package com.example.short_link.note.domain;

import java.util.List;

// Mastodon's trending link: a link card public notes carried, how many accounts shared it, how
// many notes, and the notes per day over the window, oldest first.
public record TrendingLink(
    String url,
    String title,
    String description,
    String imageUrl,
    long accounts,
    long uses,
    List<Long> history) {}
