package com.example.short_link.post.application.read;

import java.util.List;

/** GET /api/v1/public/profiles/{username}/posts/{slug} 응답. series 는 시리즈 미소속 시 null. */
public record PublicPostDetail(
    PublicAuthorView author,
    PublicPostListItem post,
    List<PublicPostBlockView> blocks,
    PublicPostSeriesNav series) {}
