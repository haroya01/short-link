package com.example.short_link.federation.infrastructure.persistence;

import com.example.short_link.federation.domain.RemoteBlockEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaRemoteBlockRepository extends JpaRepository<RemoteBlockEntity, Long> {

  Optional<RemoteBlockEntity> findByUserIdAndRemoteActorId(Long userId, Long remoteActorId);

  @Query(
      "select b, r from RemoteBlockEntity b, RemoteActorEntity r"
          + " where r.id = b.remoteActorId and b.userId = :userId order by b.id desc")
  List<Object[]> page(@Param("userId") Long userId, Pageable page);
}
