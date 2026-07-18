package com.example.short_link.analytics.domain.repository;

import com.example.short_link.analytics.domain.BehaviorEventEntity;
import java.time.Instant;
import java.util.List;

public interface BehaviorEventRepository {

  void saveAll(List<BehaviorEventEntity> events);

  int deleteByOccurredAtBefore(Instant cutoff);
}
