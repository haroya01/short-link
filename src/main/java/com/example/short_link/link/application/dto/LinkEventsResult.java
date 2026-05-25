package com.example.short_link.link.application.dto;

import java.util.List;

public record LinkEventsResult(List<LinkEventView> items, String nextCursor) {}
