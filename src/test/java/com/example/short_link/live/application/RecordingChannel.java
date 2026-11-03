package com.example.short_link.live.application;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

final class RecordingChannel implements LiveChannel {

  final List<LiveEvent> sent = new CopyOnWriteArrayList<>();
  volatile boolean broken;
  volatile boolean closed;

  @Override
  public void send(LiveEvent event) throws IOException {
    if (broken) {
      throw new IOException("client went away");
    }
    sent.add(event);
  }

  @Override
  public void close() {
    closed = true;
  }

  List<LiveEvent.Changed> changes() {
    return sent.stream()
        .filter(LiveEvent.Changed.class::isInstance)
        .map(LiveEvent.Changed.class::cast)
        .toList();
  }

  List<String> names() {
    return sent.stream().map(event -> event.getClass().getSimpleName()).toList();
  }
}
