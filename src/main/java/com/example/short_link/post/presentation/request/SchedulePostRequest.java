package com.example.short_link.post.presentation.request;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record SchedulePostRequest(@NotNull Instant scheduledAt) {}
