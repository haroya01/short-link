package com.example.short_link.user.domain.repository;

import com.example.short_link.user.domain.UserBlockEntity;
import java.util.List;
import java.util.Optional;

public interface BlockRepository {

  boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

  Optional<UserBlockEntity> findByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

  UserBlockEntity save(UserBlockEntity block);

  void delete(UserBlockEntity block);

  /**
   * Ids this user has blocked, newest edge first — drives the block list + client-side filtering.
   */
  List<Long> findBlockedIds(Long blockerId);

  /** Removes every edge the user appears on (either side) — account hard-delete path. */
  int deleteAllInvolving(Long userId);
}
