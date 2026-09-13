package com.example.short_link.user.application.read;

import java.util.List;

/** Newest follow edge first. */
public record FollowListView(List<FollowUserView> items, int page, int size, boolean hasNext) {}
