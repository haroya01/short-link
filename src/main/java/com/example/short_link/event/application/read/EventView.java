package com.example.short_link.event.application.read;

import com.example.short_link.event.domain.EventEntity;
import java.time.Instant;
import java.util.List;

/** 주최자 대시보드용. */
public record EventView(
    Long id,
    String slug,
    String title,
    String descriptionMd,
    String coverImageUrl,
    Instant startsAt,
    Instant endsAt,
    String timezone,
    String locationText,
    String locationUrl,
    String onlineUrl,
    Integer capacity,
    Instant closeAt,
    String contactField,
    String status,
    int registrationCount,
    List<EventQuestionView> questions,
    List<EventLinkView> links,
    Instant createdAt) {

  public record EventLinkView(Long linkId, String shortCode, String label) {}

  public static EventView from(
      EventEntity event,
      List<EventQuestionView> questions,
      List<EventLinkView> links,
      String coverImageUrl) {
    return new EventView(
        event.getId(),
        event.getSlug(),
        event.getTitle(),
        event.getDescriptionMd(),
        coverImageUrl,
        event.getStartsAt(),
        event.getEndsAt(),
        event.getTimezone(),
        event.getLocationText(),
        event.getLocationUrl(),
        event.getOnlineUrl(),
        event.getCapacity(),
        event.getCloseAt(),
        event.getContactField().name(),
        event.getStatus().name(),
        event.getRegistrationCount(),
        questions,
        links,
        event.getCreatedAt());
  }
}
