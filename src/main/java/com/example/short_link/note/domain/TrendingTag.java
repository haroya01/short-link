package com.example.short_link.note.domain;

import java.util.List;

// Mastodon's trending hashtag: how many accounts used it, how many notes carried it, and the
// notes per day over the window, oldest first.
public record TrendingTag(String tag, long accounts, long uses, List<Long> history) {}
