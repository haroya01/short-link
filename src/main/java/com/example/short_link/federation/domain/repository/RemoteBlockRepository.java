package com.example.short_link.federation.domain.repository;

import com.example.short_link.federation.domain.OnRemoteAccount;
import com.example.short_link.federation.domain.RemoteBlockEntity;
import java.util.List;
import java.util.Optional;

public interface RemoteBlockRepository {

  Optional<RemoteBlockEntity> find(Long userId, Long remoteActorId);

  RemoteBlockEntity save(RemoteBlockEntity block);

  void delete(RemoteBlockEntity block);

  List<OnRemoteAccount<RemoteBlockEntity>> page(Long userId, int offset, int limit);
}
