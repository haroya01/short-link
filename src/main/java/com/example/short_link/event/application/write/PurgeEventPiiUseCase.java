package com.example.short_link.event.application.write;

import com.example.short_link.event.domain.EventEntity;
import com.example.short_link.event.domain.repository.EventRegistrationRepository;
import com.example.short_link.event.domain.repository.EventRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 계정 없는 참가자 PII (이름/연락처/답변) 는 이벤트 종료 30일 후 파기 — 수집 최소화 원칙. 신청 행 자체는 남아 집계(신청 수·채널)는 유지된다. */
@Service
@RequiredArgsConstructor
public class PurgeEventPiiUseCase {

  public static final Duration RETENTION = Duration.ofDays(30);
  private static final int BATCH = 100;

  private final EventRepository eventRepository;
  private final EventRegistrationRepository registrationRepository;

  @Transactional
  public int execute(Instant now) {
    List<EventEntity> candidates = eventRepository.findPurgeCandidates(now.minus(RETENTION), BATCH);
    int purged = 0;
    for (EventEntity event : candidates) {
      purged += registrationRepository.purgePiiByEventId(event.getId());
      event.markPiiPurged(now);
    }
    return purged;
  }
}
