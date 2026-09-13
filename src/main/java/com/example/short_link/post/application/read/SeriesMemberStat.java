package com.example.short_link.post.application.read;

/**
 * {@code episode} is 1-based. {@code uniqueReaders} counts distinct human visitor hashes; {@code
 * continuedToNext} counts their overlap with the next episode and is zero on the last. The continue
 * rate is {@code continuedToNext / uniqueReaders}.
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
