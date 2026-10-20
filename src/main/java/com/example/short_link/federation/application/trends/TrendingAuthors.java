package com.example.short_link.federation.application.trends;

import com.example.short_link.federation.application.RemoteActorResolver;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TrendingAuthors {

  static final int REMEMBERED = 2_000;

  private final RemoteActorResolver resolver;
  private final Set<String> asked = ConcurrentHashMap.newKeySet();

  @Async("webhookExecutor")
  public void warm(List<String> actorUris) {
    for (String actorUri : actorUris) {
      if (asked.size() >= REMEMBERED) {
        asked.clear();
      }
      if (asked.add(actorUri)) {
        resolver.byActorUri(actorUri);
      }
    }
  }
}
