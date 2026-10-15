package com.example.short_link.federation.application;

import com.example.short_link.federation.domain.NodeUsage;
import com.example.short_link.federation.domain.repository.NodeUsageReader;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NodeInfoService {

  public static final String USAGE_CACHE = "nodeinfo-usage";

  private final NodeUsageReader usage;
  private final Clock clock;

  @Cacheable(value = USAGE_CACHE, key = "'usage'")
  public NodeUsage usage() {
    return usage.read(clock.instant());
  }
}
