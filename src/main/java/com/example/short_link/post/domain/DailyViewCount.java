package com.example.short_link.post.domain;

import java.time.LocalDate;

public record DailyViewCount(LocalDate date, long views) {}
