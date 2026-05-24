package com.example.short_link.link.presentation.response;

import java.util.List;

public record MyLinksPage(List<MyLinkResponse> items, String nextCursor, boolean hasMore) {}
