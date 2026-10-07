package com.example.short_link.notification.domain.repository;

import java.util.List;

public interface NotificationFollowerReader {

  List<Long> followerIdsOf(Long authorUserId);

  List<Long> noteSubscribersOf(Long authorUserId);

  List<Long> noteSharersOf(Long noteId, Long authorUserId);
}
