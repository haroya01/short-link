package com.example.short_link.user.infrastructure.persistence;

import com.example.short_link.user.domain.UserBlockEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaBlockRepository extends JpaRepository<UserBlockEntity, Long> {

  boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

  Optional<UserBlockEntity> findByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

  @Query(
      "select b.blockedId from UserBlockEntity b where b.blockerId = :id"
          + " order by b.createdAt desc, b.id desc")
  List<Long> findBlockedIds(@Param("id") Long blockerId);

  @Modifying
  @Query("delete from UserBlockEntity b where b.blockerId = :userId or b.blockedId = :userId")
  int deleteAllInvolving(@Param("userId") Long userId);
}
