package com.example.short_link.link.presentation.response;

import java.util.List;

public record LinkEventsPage(List<LinkEventResponse> items, String nextCursor) {}
