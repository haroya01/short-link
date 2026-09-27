package com.example.short_link.link.health.application;

import com.example.short_link.link.health.domain.DestinationFailure;

public record DestinationBrokenEvent(
    Long userId, String shortCode, String label, DestinationFailure failure, Integer httpStatus) {}
