package com.example.short_link.link.api;

import java.util.List;

public record LinkEventsPage(List<LinkEventResponse> items, String nextCursor) {}
