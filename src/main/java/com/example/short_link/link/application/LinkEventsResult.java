package com.example.short_link.link.application;

import com.example.short_link.link.application.dto.LinkEventView;
import java.util.List;

public record LinkEventsResult(List<LinkEventView> items, String nextCursor) {}
