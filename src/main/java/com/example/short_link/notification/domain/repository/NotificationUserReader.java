package com.example.short_link.notification.domain.repository;

import com.example.short_link.notification.domain.NotificationUser;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NotificationUserReader {
  Optional<NotificationUser> findById(Long userId);

  List<NotificationUser> findAllByIdIn(Collection<Long> userIds);
}
