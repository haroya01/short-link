package com.example.short_link.common.live;

import java.util.Optional;

public interface LiveScopeReader {

  LiveTopic.Scope scope();

  Optional<LiveScope> open(long id, long viewerId, String clientIp);

  long latest(long id);
}
