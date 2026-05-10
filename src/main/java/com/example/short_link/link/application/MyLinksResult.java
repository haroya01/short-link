package com.example.short_link.link.application;

import java.util.List;

public record MyLinksResult(List<MyLink> items, String nextCursor, boolean hasMore) {}
