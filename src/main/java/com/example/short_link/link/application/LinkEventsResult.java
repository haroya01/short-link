package com.example.short_link.link.application;

import java.util.List;

public record LinkEventsResult(List<LinkEventView> items, String nextCursor) {}
