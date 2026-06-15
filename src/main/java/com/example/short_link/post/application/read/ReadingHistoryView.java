package com.example.short_link.post.application.read;

import java.util.List;

/** A page of the reader's history (most recently read first). */
public record ReadingHistoryView(
    List<ReadingHistoryEntryView> items, int page, int size, boolean hasNext) {}
