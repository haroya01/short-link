package com.example.short_link.user.infrastructure.persistence;

import com.example.short_link.user.domain.UserMuteEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaMuteRepository extends JpaRepository<UserMuteEntity, Long> {

  Optional<UserMuteEntity> findByUserIdAndMutedUserId(Long userId, Long mutedUserId);

  @Query(
      "select m from UserMuteEntity m where m.userId = :id"
          + " and (m.expiresAt is null or m.expiresAt > :now) order by m.createdAt desc, m.id desc")
  List<UserMuteEntity> findActive(@Param("id") Long userId, @Param("now") Instant now);

  @Modifying
  @Query("delete from UserMuteEntity m where m.userId = :userId or m.mutedUserId = :userId")
  int deleteAllInvolving(@Param("userId") Long userId);
}
