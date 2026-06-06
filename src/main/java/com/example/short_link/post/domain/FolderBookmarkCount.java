package com.example.short_link.post.domain;

/** How many bookmarks a user has filed under one folder — drives the folder pill counts. */
public record FolderBookmarkCount(Long folderId, long count) {}
