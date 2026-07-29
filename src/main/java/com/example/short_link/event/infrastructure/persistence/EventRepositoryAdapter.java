package com.example.short_link.event.infrastructure.persistence;

import com.example.short_link.event.domain.EventEntity;
import com.example.short_link.event.domain.repository.EventRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class EventRepositoryAdapter implements EventRepository {

  private final JpaEventRepository jpa;

  @Override
  public EventEntity save(EventEntity event) {
    return jpa.save(event);
  }

  @Override
  public Optional<EventEntity> findById(Long id) {
    return jpa.findById(id);
  }

  @Override
  public Optional<EventEntity> findBySlug(String slug) {
    return jpa.findBySlug(slug);
  }

  @Override
  public List<EventEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId) {
    return jpa.findAllByUserIdOrderByCreatedAtDesc(userId);
  }

  @Override
  public int tryIncrementRegistrationCount(Long eventId) {
    return jpa.tryIncrementRegistrationCount(eventId);
  }

  @Override
  public int decrementRegistrationCount(Long eventId) {
    return jpa.decrementRegistrationCount(eventId);
  }

  @Override
  public List<EventEntity> findPurgeCandidates(Instant endedBefore, int limit) {
    return jpa.findPurgeCandidates(endedBefore, PageRequest.of(0, limit));
  }
}
