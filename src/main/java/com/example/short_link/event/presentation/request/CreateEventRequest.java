package com.example.short_link.event.presentation.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public record CreateEventRequest(
    @NotBlank @Size(max = 200) String title,
    @Size(max = 50000) String descriptionMd,
    @NotNull Instant startsAt,
    Instant endsAt,
    @NotBlank @Size(max = 40) String timezone,
    @Size(max = 200) String locationText,
    @Size(max = 2048) String locationUrl,
    @Size(max = 2048) String onlineUrl,
    @Min(1) @Max(10000) Integer capacity,
    Instant closeAt,
    String contactField,
    @Valid @Size(max = 10) List<QuestionSpecRequest> questions) {}
