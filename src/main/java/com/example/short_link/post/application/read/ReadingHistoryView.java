package com.example.short_link.post.application.read;

import java.util.List;

public record ReadingHistoryView(
    List<ReadingHistoryEntryView> items, int page, int size, boolean hasNext) {}
