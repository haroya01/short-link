package com.example.short_link.user.infrastructure.persistence;

import com.example.short_link.user.domain.UserBlockEntity;
import com.example.short_link.user.domain.repository.BlockRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class BlockRepositoryAdapter implements BlockRepository {

  private final JpaBlockRepository jpa;

  @Override
  public boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId) {
    return jpa.existsByBlockerIdAndBlockedId(blockerId, blockedId);
  }

  @Override
  public Optional<UserBlockEntity> findByBlockerIdAndBlockedId(Long blockerId, Long blockedId) {
    return jpa.findByBlockerIdAndBlockedId(blockerId, blockedId);
  }

  @Override
  public UserBlockEntity save(UserBlockEntity block) {
    return jpa.save(block);
  }

  @Override
  public void delete(UserBlockEntity block) {
    jpa.delete(block);
  }

  @Override
  public List<Long> findBlockedIds(Long blockerId) {
    return jpa.findBlockedIds(blockerId);
  }

  @Override
  public int deleteAllInvolving(Long userId) {
    return jpa.deleteAllInvolving(userId);
  }
}
