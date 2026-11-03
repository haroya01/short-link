package com.example.short_link.notification.domain.repository;

import java.util.Optional;

public interface NotificationRoomReader {

  Optional<String> name(Long roomId);
}
