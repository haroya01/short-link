package com.example.short_link.event.infrastructure.persistence;

import com.example.short_link.event.domain.EventQuestionEntity;
import com.example.short_link.event.domain.repository.EventQuestionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class EventQuestionRepositoryAdapter implements EventQuestionRepository {

  private final JpaEventQuestionRepository jpa;

  @Override
  public List<EventQuestionEntity> saveAll(List<EventQuestionEntity> questions) {
    return jpa.saveAll(questions);
  }

  @Override
  public List<EventQuestionEntity> findAllByEventIdOrderByPosition(Long eventId) {
    return jpa.findAllByEventIdOrderByPosition(eventId);
  }

  @Override
  public void deleteAllByEventId(Long eventId) {
    jpa.deleteAllByEventId(eventId);
  }
}
