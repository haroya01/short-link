package com.example.short_link.event.infrastructure.persistence;

import com.example.short_link.event.domain.EventQuestionEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaEventQuestionRepository extends JpaRepository<EventQuestionEntity, Long> {

  List<EventQuestionEntity> findAllByEventIdOrderByPosition(Long eventId);

  void deleteAllByEventId(Long eventId);
}
