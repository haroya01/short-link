package com.example.short_link.notification.domain.repository;

import java.util.List;

public interface NotificationFollowerReader {

  List<Long> followerIdsOf(Long authorUserId);
}
