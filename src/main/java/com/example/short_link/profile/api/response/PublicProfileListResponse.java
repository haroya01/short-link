package com.example.short_link.profile.api.response;

import java.util.List;

public record PublicProfileListResponse(List<PublicProfileHandleItem> items, long total) {}
