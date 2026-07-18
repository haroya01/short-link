package com.example.short_link.analytics.infrastructure.persistence;

import com.example.short_link.analytics.domain.BehaviorEventEntity;
import com.example.short_link.analytics.domain.repository.BehaviorEventRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class BehaviorEventRepositoryAdapter implements BehaviorEventRepository {

  private final JpaBehaviorEventRepository jpa;

  @Override
  public void saveAll(List<BehaviorEventEntity> events) {
    jpa.saveAll(events);
  }

  @Override
  public int deleteByOccurredAtBefore(Instant cutoff) {
    return jpa.deleteByOccurredAtBefore(cutoff);
  }
}
