package com.example.short_link.federation.infrastructure.persistence;

import com.example.short_link.federation.domain.OnRemoteAccount;
import com.example.short_link.federation.domain.RemoteActorEntity;
import com.example.short_link.federation.domain.RemoteBlockEntity;
import com.example.short_link.federation.domain.repository.RemoteBlockRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class RemoteBlockRepositoryAdapter implements RemoteBlockRepository {

  private final JpaRemoteBlockRepository jpa;

  @Override
  public Optional<RemoteBlockEntity> find(Long userId, Long remoteActorId) {
    return jpa.findByUserIdAndRemoteActorId(userId, remoteActorId);
  }

  @Override
  public RemoteBlockEntity save(RemoteBlockEntity block) {
    return jpa.save(block);
  }

  @Override
  public void delete(RemoteBlockEntity block) {
    jpa.delete(block);
  }

  @Override
  public List<OnRemoteAccount<RemoteBlockEntity>> page(Long userId, int offset, int limit) {
    return jpa.page(userId, PageRequest.of(offset / limit, limit)).stream()
        .map(row -> new OnRemoteAccount<>((RemoteBlockEntity) row[0], (RemoteActorEntity) row[1]))
        .toList();
  }
}
