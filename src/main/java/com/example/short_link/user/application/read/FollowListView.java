package com.example.short_link.user.application.read;

import java.util.List;

/** A page of a followers / following list (newest edge first). */
public record FollowListView(List<FollowUserView> items, int page, int size, boolean hasNext) {}
