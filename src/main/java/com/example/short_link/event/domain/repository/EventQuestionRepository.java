package com.example.short_link.event.domain.repository;

import com.example.short_link.event.domain.EventQuestionEntity;
import java.util.List;

public interface EventQuestionRepository {

  <S extends EventQuestionEntity> List<S> saveAll(Iterable<S> questions);

  List<EventQuestionEntity> findAllByEventIdOrderByPosition(Long eventId);

  void deleteAllByEventId(Long eventId);
}
