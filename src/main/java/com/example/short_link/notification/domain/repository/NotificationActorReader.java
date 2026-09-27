package com.example.short_link.notification.domain.repository;

import com.example.short_link.notification.domain.NotificationActor;
import java.util.Collection;
import java.util.Map;

public interface NotificationActorReader {

  Map<Long, NotificationActor> resolve(Collection<Long> userIds);
}
