package com.example.short_link.post.application.read;

import java.time.LocalDate;

/** Daily analytics include every day in the window, with zero for days without views. */
public record DailyPoint(LocalDate date, long views) {}
