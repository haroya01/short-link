package com.example.short_link.post.application.read;

/**
 * One entry in a reader's bookmark list (reading list). Mirrors the frontend BookmarkItem: the
 * bookmarked post's id + the author handle and post slug/title needed to link to it.
 */
public record BookmarkView(Long id, String username, String title, String slug) {}
