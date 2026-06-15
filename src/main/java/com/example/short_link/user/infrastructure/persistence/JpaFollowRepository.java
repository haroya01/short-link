package com.example.short_link.user.infrastructure.persistence;

import com.example.short_link.user.domain.FollowEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
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

  @Query(
      "select f.followerId from FollowEntity f where f.followingId = :id"
          + " order by f.createdAt desc, f.id desc")
  List<Long> findFollowerIdsPaged(@Param("id") Long followingId, Pageable pageable);

  @Query(
      "select f.followingId from FollowEntity f where f.followerId = :id"
          + " order by f.createdAt desc, f.id desc")
  List<Long> findFollowingIdsPaged(@Param("id") Long followerId, Pageable pageable);

  @Query(
      "select f.followingId from FollowEntity f"
          + " where f.followerId = :viewer and f.followingId in :ids")
  List<Long> findFollowedAmong(@Param("viewer") Long viewerId, @Param("ids") Collection<Long> ids);

  @Query(
      "select f.followingId, count(f) from FollowEntity f"
          + " where f.followingId in :ids group by f.followingId")
  List<Object[]> countFollowersByIdIn(@Param("ids") Collection<Long> ids);

  @Modifying
  @Query("delete from FollowEntity f where f.followerId = :userId or f.followingId = :userId")
  int deleteAllInvolving(@Param("userId") Long userId);
}
