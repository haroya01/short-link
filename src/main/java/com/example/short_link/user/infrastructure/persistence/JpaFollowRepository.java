package com.example.short_link.user.infrastructure.persistence;

import com.example.short_link.user.domain.FollowEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaFollowRepository extends JpaRepository<FollowEntity, Long> {

  boolean existsByFollowerIdAndFollowingId(Long followerId, Long followingId);

  Optional<FollowEntity> findByFollowerIdAndFollowingId(Long followerId, Long followingId);

  long countByFollowingId(Long followingId);

  long countByFollowerId(Long followerId);

  @Query("select f.followingId from FollowEntity f where f.followerId = :followerId")
  List<Long> findFollowingIds(@Param("followerId") Long followerId);

  @Modifying
  @Query("delete from FollowEntity f where f.followerId = :userId or f.followingId = :userId")
  int deleteAllInvolving(@Param("userId") Long userId);
}
