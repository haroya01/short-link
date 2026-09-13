package com.example.short_link.notification.domain.repository;

import com.example.short_link.notification.domain.NotificationActor;
import java.util.Collection;
import java.util.Map;

public interface NotificationActorReader {

  /** Identity for each given user id; ids with no (or a deleted) user are simply absent. */
  Map<Long, NotificationActor> resolve(Collection<Long> userIds);
}
