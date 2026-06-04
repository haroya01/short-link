package com.example.short_link.post.application.read;

/**
 * One episode's performance within a series, plus its read-through to the next episode. {@code
 * episode} is the 1-based position in series order. {@code uniqueReaders} counts distinct human
 * fingerprints (visitor_hash); {@code continuedToNext} is how many of them also read the next
 * episode — so the funnel's continue-rate is {@code continuedToNext / uniqueReaders}. The last
 * episode has {@code continuedToNext == 0} (nothing follows it).
 */
public record SeriesMemberStat(
    Long postId,
    String slug,
    String title,
    int episode,
    long views,
    long likes,
    long follows,
    long uniqueReaders,
    long continuedToNext) {}
