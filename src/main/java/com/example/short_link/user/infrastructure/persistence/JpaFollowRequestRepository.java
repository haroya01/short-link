package com.example.short_link.user.infrastructure.persistence;

import com.example.short_link.user.domain.FollowRequestEntity;
import com.example.short_link.user.domain.PendingFollowRequest;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaFollowRequestRepository extends JpaRepository<FollowRequestEntity, Long> {

  boolean existsByFollowerIdAndFollowingId(Long followerId, Long followingId);

  @Query(
      "select new com.example.short_link.user.domain.PendingFollowRequest("
          + "u.id, u.username, u.displayName, u.avatarUrl, r.createdAt)"
          + " from FollowRequestEntity r, UserEntity u"
          + " where u.id = r.followerId and r.followingId = :owner"
          + " and u.deletedAt is null and u.username is not null"
          + " and not exists (select 1 from UserBlockEntity b"
          + " where b.blockerId = :owner and b.blockedId = u.id)"
          + " order by r.id desc")
  List<PendingFollowRequest> pending(@Param("owner") Long owner, Pageable page);

  @Modifying
  @Query(
      "delete from FollowRequestEntity r where r.followerId = :followerId"
          + " and r.followingId = :followingId")
  int deleteRequest(@Param("followerId") Long followerId, @Param("followingId") Long followingId);
}
