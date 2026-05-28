package com.example.short_link.post.application.read;

import java.util.List;

/** GET /api/v1/public/profiles/{username}/posts/{slug} 응답. */
public record PublicPostDetail(
    PublicAuthorView author, PublicPostListItem post, List<PublicPostBlockView> blocks) {}
