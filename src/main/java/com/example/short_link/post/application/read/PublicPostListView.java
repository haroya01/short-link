package com.example.short_link.post.application.read;

import java.util.List;

/** GET /api/v1/public/profiles/{username}/posts 응답. */
public record PublicPostListView(PublicAuthorView author, List<PublicPostListItem> posts) {}
