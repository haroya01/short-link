package com.example.short_link.live.application;

import com.example.short_link.common.live.LiveScope;
import com.example.short_link.common.live.LiveTopic;

public final class LiveSubscriber {

  private final long userId;
  private final LiveTopic topic;
  private final LiveScope scope;
  private final LiveChannel channel;
  private long cursor;
  private boolean ready;
  private boolean missed;

  public LiveSubscriber(long userId, LiveTopic topic, LiveScope scope, LiveChannel channel) {
    this.userId = userId;
    this.topic = topic;
    this.scope = scope;
    this.channel = channel;
  }

  public long userId() {
    return userId;
  }

  public LiveTopic topic() {
    return topic;
  }

  LiveScope scope() {
    return scope;
  }

  LiveChannel channel() {
    return channel;
  }

  long cursor() {
    return cursor;
  }

  boolean ready() {
    return ready;
  }

  boolean missed() {
    return missed;
  }

  void advance(long latest) {
    cursor = Math.max(cursor, latest);
  }

  void markReady() {
    ready = true;
  }

  void miss() {
    missed = true;
  }
}
