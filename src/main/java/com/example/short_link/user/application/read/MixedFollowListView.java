package com.example.short_link.user.application.read;

import java.util.List;

public record MixedFollowListView(
    List<FollowListItem> items, int page, int size, boolean hasNext) {}
