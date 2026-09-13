package com.example.short_link.post.presentation.request;

/** {@code folderId == null} unfiles the bookmark. */
public record MoveBookmarkRequest(Long folderId) {}
