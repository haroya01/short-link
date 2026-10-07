package com.example.short_link.common.event;

public record RemoteDomainBlockedEvent(Long userId, String domain) {}
