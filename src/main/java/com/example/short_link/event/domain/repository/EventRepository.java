package com.example.short_link.event.domain.repository;

import com.example.short_link.event.domain.EventEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EventRepository {

  EventEntity save(EventEntity event);

  Optional<EventEntity> findById(Long id);

  Optional<EventEntity> findBySlug(String slug);

  List<EventEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId);

  /** 정원 원자 판정 — capacity 미만일 때만 registration_count 를 +1. 0 rows 면 만석. 마지막 1자리 동시 신청도 DB 가 직렬화한다. */
  int tryIncrementRegistrationCount(Long eventId);

  int decrementRegistrationCount(Long eventId);

  List<EventEntity> findPurgeCandidates(Instant endedBefore, int limit);
}
