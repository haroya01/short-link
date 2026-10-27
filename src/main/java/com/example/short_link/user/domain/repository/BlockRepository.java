package com.example.short_link.user.domain.repository;

import com.example.short_link.common.user.BlockEdge;
import com.example.short_link.user.domain.UserBlockEntity;
import java.util.List;
import java.util.Optional;

public interface BlockRepository {

  boolean exists(BlockEdge edge);

  Optional<UserBlockEntity> find(BlockEdge edge);

  List<UserBlockEntity> findBetween(Long a, Long b);

  UserBlockEntity save(UserBlockEntity block);

  void delete(UserBlockEntity block);

  List<Long> findBlockedIds(Long blockerId);

  int deleteAllInvolving(Long userId);
}
