package com.example.short_link.user.domain.repository;

import com.example.short_link.user.domain.UserBlockEntity;
import java.util.List;
import java.util.Optional;

public interface BlockRepository {

  boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

  Optional<UserBlockEntity> findByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

  UserBlockEntity save(UserBlockEntity block);

  void delete(UserBlockEntity block);

  /** Newest block edge first. */
  List<Long> findBlockedIds(Long blockerId);

  /** Removes edges where the user appears on either side. */
  int deleteAllInvolving(Long userId);
}
