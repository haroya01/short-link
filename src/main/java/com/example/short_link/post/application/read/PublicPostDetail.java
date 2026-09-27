package com.example.short_link.post.application.read;

import java.util.List;

public record PublicPostDetail(
    PublicAuthorView author,
    PublicPostListItem post,
    List<PublicPostBlockView> blocks,
    PublicPostSeriesNav series) {}
