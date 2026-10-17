package com.example.short_link.federation.application.relay;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// Subscribes off the startup thread: each relay is fetched first, and readiness waits for no one.
@Slf4j
@Component
@RequiredArgsConstructor
public class RelayStartup {

  private final RelaySubscriptions subscriptions;
  private final RelayProperties props;

  @EventListener(ApplicationReadyEvent.class)
  public void ready() {
    if (props.enabled() && !props.subscriptions().isEmpty()) {
      Thread.ofVirtual().name("kurl-relay-subscribe").start(this::subscribeListed);
    }
  }

  public int subscribeListed() {
    if (!props.enabled()) {
      return 0;
    }
    Set<String> known =
        subscriptions.list().relays().stream()
            .map(RelaySubscriptions.View::actorUri)
            .collect(Collectors.toCollection(HashSet::new));
    int asked = 0;
    for (String uri : props.subscriptions()) {
      if (known.contains(uri)) {
        continue;
      }
      try {
        RelaySubscriptions.View view = subscriptions.subscribe(uri);
        known.add(view.actorUri());
        asked++;
        log.info(
            "federation relay subscribed from environment relay={} state={}", uri, view.state());
      } catch (RuntimeException failed) {
        log.warn(
            "federation relay could not subscribe from environment relay={} reason={}",
            uri,
            failed.toString());
      }
    }
    return asked;
  }
}
