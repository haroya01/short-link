package com.example.short_link.federation.infrastructure.persistence;

import com.example.short_link.federation.domain.RemoteMuteEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaRemoteMuteRepository extends JpaRepository<RemoteMuteEntity, Long> {

  Optional<RemoteMuteEntity> findByUserIdAndRemoteActorId(Long userId, Long remoteActorId);

  @Query(
      "select m, r from RemoteMuteEntity m, RemoteActorEntity r"
          + " where r.id = m.remoteActorId and m.userId = :userId"
          + " and (m.expiresAt is null or m.expiresAt > :now) order by m.id desc")
  List<Object[]> active(@Param("userId") Long userId, @Param("now") Instant now, Pageable page);
}
