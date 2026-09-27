package com.example.short_link.post.application.read;

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
