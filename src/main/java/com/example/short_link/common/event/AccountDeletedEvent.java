package com.example.short_link.common.event;

// Published when an account is soft-deleted (the start of the 30-day grace period).
public record AccountDeletedEvent(Long userId) {}
