package com.example.short_link.analytics.domain.repository;

import com.example.short_link.analytics.domain.BehaviorEventEntity;
import java.time.Instant;
import java.util.List;

public interface BehaviorEventRepository {

  <S extends BehaviorEventEntity> List<S> saveAll(Iterable<S> events);

  int deleteByOccurredAtBefore(Instant cutoff);
}
