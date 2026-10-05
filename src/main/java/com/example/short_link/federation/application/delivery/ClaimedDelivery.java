package com.example.short_link.federation.application.delivery;

import java.time.Instant;

public record ClaimedDelivery(
    Long id, String inbox, Long signerUserId, String body, int attempts, Instant createdAt) {}
