package com.example.short_link.post.application.read;

/** A bookmark folder plus how many bookmarks are filed under it — drives the folder filter bar. */
public record BookmarkFolderView(long id, String name, long count) {}
