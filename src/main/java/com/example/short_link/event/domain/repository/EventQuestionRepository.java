package com.example.short_link.event.domain.repository;

import com.example.short_link.event.domain.EventQuestionEntity;
import java.util.List;

public interface EventQuestionRepository {

  List<EventQuestionEntity> saveAll(List<EventQuestionEntity> questions);

  List<EventQuestionEntity> findAllByEventIdOrderByPosition(Long eventId);

  void deleteAllByEventId(Long eventId);
}
