package com.example.short_link.live.presentation;

import com.example.short_link.live.application.LiveChannel;
import com.example.short_link.live.application.LiveEvent;
import java.io.IOException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

final class SseLiveChannel implements LiveChannel {

  private final SseEmitter emitter;

  SseLiveChannel(SseEmitter emitter) {
    this.emitter = emitter;
  }

  @Override
  public void send(LiveEvent event) throws IOException {
    emitter.send(
        switch (event) {
          case LiveEvent.Ready ready ->
              SseEmitter.event().name("ready").reconnectTime(ready.retryMs()).data(ready);
          case LiveEvent.Bye bye -> SseEmitter.event().name("bye").data(bye);
          case LiveEvent.Changed changed ->
              SseEmitter.event().name("changed").id(changed.cursor()).data(changed);
          case LiveEvent.Revoked revoked -> SseEmitter.event().name("revoked").data(revoked);
          case LiveEvent.Busy busy -> SseEmitter.event().name("busy").data(busy);
          case LiveEvent.Heartbeat heartbeat -> SseEmitter.event().comment("");
        });
  }

  @Override
  public void close() {
    emitter.complete();
  }
}
