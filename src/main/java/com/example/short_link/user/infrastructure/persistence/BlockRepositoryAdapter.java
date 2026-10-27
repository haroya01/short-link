package com.example.short_link.user.infrastructure.persistence;

import com.example.short_link.common.user.BlockEdge;
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
  public boolean exists(BlockEdge edge) {
    return jpa.existsByBlockerIdAndBlockedId(edge.blocker(), edge.blocked());
  }

  @Override
  public List<UserBlockEntity> findBetween(Long a, Long b) {
    return jpa.findBetween(a, b);
  }

  @Override
  public Optional<UserBlockEntity> find(BlockEdge edge) {
    return jpa.findByBlockerIdAndBlockedId(edge.blocker(), edge.blocked());
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
