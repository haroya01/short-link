package com.example.short_link.link.domain.repository;

import com.example.short_link.link.domain.LinkExpiryFilter;
import java.time.Instant;
import java.util.Collection;

public record MyLinksSearchCriteria(
    Long userId,
    String text,
    Collection<Long> linkIds,
    String domain,
    LinkExpiryFilter expiry,
    Instant createdAfter,
    Instant createdBefore) {}
