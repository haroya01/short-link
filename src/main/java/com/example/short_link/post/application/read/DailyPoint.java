package com.example.short_link.post.application.read;

import java.time.LocalDate;

/**
 * One point on the analytics view-over-time line. Every day in the window is present (filled 0).
 */
public record DailyPoint(LocalDate date, long views) {}
