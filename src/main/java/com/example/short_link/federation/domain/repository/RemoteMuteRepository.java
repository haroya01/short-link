package com.example.short_link.federation.domain.repository;

import com.example.short_link.federation.domain.OnRemoteAccount;
import com.example.short_link.federation.domain.RemoteMuteEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RemoteMuteRepository {

  Optional<RemoteMuteEntity> find(Long userId, Long remoteActorId);

  RemoteMuteEntity save(RemoteMuteEntity mute);

  void delete(RemoteMuteEntity mute);

  List<OnRemoteAccount<RemoteMuteEntity>> active(Long userId, Instant now, int offset, int limit);
}
