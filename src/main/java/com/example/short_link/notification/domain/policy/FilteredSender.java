package com.example.short_link.notification.domain.policy;

import java.time.Instant;

public record FilteredSender(Long actorUserId, Long actorRemoteId, Long count, Instant lastAt) {}
