package com.example.short_link.live.application;

import com.example.short_link.common.live.LiveScope;
import com.example.short_link.common.live.LiveScopeReader;
import com.example.short_link.common.live.LiveTopic;
import com.example.short_link.common.security.ClickStreamTokenService;
import com.example.short_link.live.exception.LiveErrorCode;
import com.example.short_link.live.exception.LiveException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class LiveStreamService {

  public static final int RETRY_AFTER_SECONDS = LiveStreams.RETRY_AFTER_SECONDS;

  private final Map<LiveTopic.Scope, LiveScopeReader> readers =
      new EnumMap<>(LiveTopic.Scope.class);
  private final ClickStreamTokenService tokens;
  private final LiveStreams streams;
  private final LiveProperties props;

  public LiveStreamService(
      List<LiveScopeReader> readers,
      ClickStreamTokenService tokens,
      LiveStreams streams,
      LiveProperties props) {
    readers.forEach(reader -> this.readers.put(reader.scope(), reader));
    this.tokens = tokens;
    this.streams = streams;
    this.props = props;
  }

  public String issueToken(LiveTopic topic, long viewerId, String clientIp) {
    if (open(topic, viewerId, clientIp).isEmpty()) {
      throw new LiveException(LiveErrorCode.LIVE_SCOPE_NOT_FOUND);
    }
    if (!streams.admits(viewerId)) {
      throw new LiveException(LiveErrorCode.LIVE_STREAM_LIMIT, RETRY_AFTER_SECONDS)
          .with("retryAfterSeconds", RETRY_AFTER_SECONDS);
    }
    return tokens.createStreamToken(viewerId, topic.tokenScope(), props.tokenTtl());
  }

  public Optional<Long> holder(LiveTopic topic, String streamToken) {
    if (streamToken == null || streamToken.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.ofNullable(tokens.parseStreamToken(streamToken, topic.tokenScope()));
    } catch (RuntimeException invalid) {
      return Optional.empty();
    }
  }

  public Optional<LiveScope> open(LiveTopic topic, long viewerId, String clientIp) {
    LiveScopeReader reader = readers.get(topic.scope());
    return reader == null ? Optional.empty() : reader.open(topic.id(), viewerId, clientIp);
  }

  public boolean join(LiveSubscriber subscriber) {
    return streams.register(subscriber);
  }

  public void begin(LiveSubscriber subscriber, Long since) {
    LiveTopic topic = subscriber.topic();
    streams.start(
        subscriber,
        readers.get(topic.scope()).latest(topic.id()),
        since,
        tokens.createStreamToken(subscriber.userId(), topic.tokenScope(), props.tokenTtl()));
  }

  public void leave(LiveSubscriber subscriber) {
    streams.remove(subscriber);
  }
}
