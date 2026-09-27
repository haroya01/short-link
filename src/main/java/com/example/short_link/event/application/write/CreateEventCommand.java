package com.example.short_link.event.application.write;

import com.example.short_link.event.application.helper.EventQuestions.QuestionSpec;
import com.example.short_link.event.domain.ContactField;
import java.time.Instant;
import java.util.List;

public record CreateEventCommand(
    Long userId,
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
    ContactField contactField,
    List<QuestionSpec> questions) {}
