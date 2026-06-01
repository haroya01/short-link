package com.example.short_link.post.application.read;

/**
 * One entry in a reader's liked-posts list. Same shape as the bookmark/reading-list item: the
 * post's id + the author handle and post slug/title needed to link to it.
 */
public record LikedPostView(Long id, String username, String title, String slug) {}
