package com.example.short_link.post.domain;

// One entry of the subscription feed; a note names the subscribed series that brought it.
public record FollowingFeedRef(SeriesItemType type, Long id, Long seriesId) {}
