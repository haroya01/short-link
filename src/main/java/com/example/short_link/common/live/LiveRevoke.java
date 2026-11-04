package com.example.short_link.common.live;

import java.util.Objects;

public sealed interface LiveRevoke {

  boolean ends(long subscriberId, LiveTopic watched, LiveScope scope);

  record Closed(LiveTopic topic) implements LiveRevoke {
    @Override
    public boolean ends(long subscriberId, LiveTopic watched, LiveScope scope) {
      return topic.equals(watched);
    }
  }

  record Recheck(LiveTopic topic) implements LiveRevoke {
    @Override
    public boolean ends(long subscriberId, LiveTopic watched, LiveScope scope) {
      return topic.equals(watched);
    }
  }

  record LeftRoom(long roomId, long userId) implements LiveRevoke {
    @Override
    public boolean ends(long subscriberId, LiveTopic watched, LiveScope scope) {
      return subscriberId == userId
          && (watched.equals(LiveTopic.room(roomId)) || Objects.equals(scope.roomId(), roomId));
    }
  }

  record Blocked(long blockerId, long blockedId) implements LiveRevoke {
    @Override
    public boolean ends(long subscriberId, LiveTopic watched, LiveScope scope) {
      return (subscriberId == blockerId && Objects.equals(scope.ownerId(), blockedId))
          || (subscriberId == blockedId && Objects.equals(scope.ownerId(), blockerId));
    }
  }

  record AccountGone(long userId) implements LiveRevoke {
    @Override
    public boolean ends(long subscriberId, LiveTopic watched, LiveScope scope) {
      return subscriberId == userId || Objects.equals(scope.ownerId(), userId);
    }
  }
}
