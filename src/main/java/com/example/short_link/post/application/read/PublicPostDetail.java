package com.example.short_link.post.application.read;

import java.util.List;

/** {@code series} is null when the post has no series membership. */
public record PublicPostDetail(
    PublicAuthorView author,
    PublicPostListItem post,
    List<PublicPostBlockView> blocks,
    PublicPostSeriesNav series) {}
