package com.example.short_link.event.application.read;

import com.example.short_link.event.application.helper.EventQuestions;
import com.example.short_link.event.domain.EventRegistrationEntity;
import java.time.Instant;
import java.util.Map;

public record AttendeeView(
    Long id,
    String name,
    String contact,
    Map<String, String> answers,
    String status,
    String channel,
    Instant createdAt,
    Instant canceledAt) {

  public static AttendeeView from(EventRegistrationEntity registration, String channelLabel) {
    return new AttendeeView(
        registration.getId(),
        registration.getName(),
        registration.getContact(),
        EventQuestions.deserializeAnswers(registration.getAnswersJson()),
        registration.getStatus().name(),
        channelLabel,
        registration.getCreatedAt(),
        registration.getCanceledAt());
  }
}
