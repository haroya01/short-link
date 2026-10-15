package com.example.short_link.federation.infrastructure.persistence;

import com.example.short_link.federation.domain.OnRemoteAccount;
import com.example.short_link.federation.domain.RemoteActorEntity;
import com.example.short_link.federation.domain.RemoteMuteEntity;
import com.example.short_link.federation.domain.repository.RemoteMuteRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class RemoteMuteRepositoryAdapter implements RemoteMuteRepository {

  private final JpaRemoteMuteRepository jpa;

  @Override
  public Optional<RemoteMuteEntity> find(Long userId, Long remoteActorId) {
    return jpa.findByUserIdAndRemoteActorId(userId, remoteActorId);
  }

  @Override
  public RemoteMuteEntity save(RemoteMuteEntity mute) {
    return jpa.save(mute);
  }

  @Override
  public void delete(RemoteMuteEntity mute) {
    jpa.delete(mute);
  }

  @Override
  public List<OnRemoteAccount<RemoteMuteEntity>> active(
      Long userId, Instant now, int offset, int limit) {
    return jpa.active(userId, now, PageRequest.of(offset / limit, limit)).stream()
        .map(row -> new OnRemoteAccount<>((RemoteMuteEntity) row[0], (RemoteActorEntity) row[1]))
        .toList();
  }
}
