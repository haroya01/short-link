package com.example.short_link.post.domain;

import java.time.LocalDate;

/** UTC daily counts are sparse; days without views are absent. */
public record DailyViewCount(LocalDate date, long views) {}
