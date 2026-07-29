package com.example.short_link.event.application.read;

import com.example.short_link.event.domain.EventEntity;
import java.time.Instant;
import java.util.List;

/** 공개 페이지용 — 주최자 전용 정보(신청자 수 상세, 링크 목록)는 싣지 않는다. */
public record PublicEventView(
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
    Integer spotsLeft,
    Instant closeAt,
    String contactField,
    String status,
    boolean acceptingRegistrations,
    int attending,
    String organizerName,
    String organizerAvatarUrl,
    List<EventQuestionView> questions) {

  public static PublicEventView from(
      EventEntity event,
      List<EventQuestionView> questions,
      Instant now,
      String coverImageUrl,
      String organizerName,
      String organizerAvatarUrl) {
    return new PublicEventView(
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
        event.spotsLeft(),
        event.getCloseAt(),
        event.getContactField().name(),
        event.getStatus().name(),
        event.acceptsRegistrations(now),
        event.getRegistrationCount(),
        organizerName,
        organizerAvatarUrl,
        questions);
  }
}
