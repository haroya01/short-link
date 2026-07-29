package com.example.short_link.event.application.write;

import com.example.short_link.event.application.helper.EventQuestions.QuestionSpec;
import java.time.Instant;
import java.util.List;

public record UpdateEventCommand(
    Long userId,
    Long eventId,
    String title,
    String descriptionMd,
    Instant startsAt,
    Instant endsAt,
    String timezone,
    String locationText,
    String locationUrl,
    String onlineUrl,
    Integer capacity,
    Instant closeAt,
    List<QuestionSpec> questions) {}
