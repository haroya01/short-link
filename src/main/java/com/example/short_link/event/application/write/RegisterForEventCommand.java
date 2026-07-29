package com.example.short_link.event.application.write;

import java.util.Map;

public record RegisterForEventCommand(
    String slug,
    String name,
    String contact,
    Map<Long, String> answers,
    String clientIp,
    String userAgent) {}
