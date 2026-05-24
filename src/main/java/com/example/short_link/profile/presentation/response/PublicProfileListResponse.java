package com.example.short_link.profile.presentation.response;

import java.util.List;

public record PublicProfileListResponse(List<PublicProfileHandleItem> items, long total) {}
