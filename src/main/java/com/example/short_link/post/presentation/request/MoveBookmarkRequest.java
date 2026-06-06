package com.example.short_link.post.presentation.request;

/** File a bookmark into a folder. {@code folderId == null} unfiles it (back to auto-grouped). */
public record MoveBookmarkRequest(Long folderId) {}
