package com.example.short_link.live.application;

import com.example.short_link.common.live.LiveChange;
import com.example.short_link.common.live.LiveRevoke;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class LocalLiveBus implements LiveBus {

  private final LiveStreams streams;

  @Override
  public void publish(LiveChange change) {
    streams.offer(change);
  }

  @Override
  public void publish(LiveRevoke revoke) {
    streams.revoke(revoke);
  }
}
