package com.example.short_link.user.infrastructure.persistence;

import com.example.short_link.user.domain.FollowRequestEntity;
import com.example.short_link.user.domain.PendingFollowRequest;
import com.example.short_link.user.domain.repository.FollowRequestRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class FollowRequestRepositoryAdapter implements FollowRequestRepository {

  private final JpaFollowRequestRepository jpa;
  private final EntityManager em;

  @Override
  public boolean exists(Long followerId, Long followingId) {
    return jpa.existsByFollowerIdAndFollowingId(followerId, followingId);
  }

  @Override
  public FollowRequestEntity save(FollowRequestEntity request) {
    return jpa.save(request);
  }

  @Override
  public int delete(Long followerId, Long followingId) {
    return jpa.deleteRequest(followerId, followingId);
  }

  // Requests from accounts that are gone or that this member blocked stay out of the list.
  @Override
  public List<PendingFollowRequest> pending(Long followingId, int offset, int limit) {
    return jpa.pending(followingId, PageRequest.of(offset / limit, limit));
  }

  @Override
  public int approveAll(Long followingId) {
    em.createNativeQuery(
            "INSERT IGNORE INTO user_follow (follower_id, following_id, created_at)"
                + " SELECT follower_id, following_id, :now FROM follow_request"
                + " WHERE following_id = :owner")
        .setParameter("now", Instant.now())
        .setParameter("owner", followingId)
        .executeUpdate();
    return em.createNativeQuery("DELETE FROM follow_request WHERE following_id = :owner")
        .setParameter("owner", followingId)
        .executeUpdate();
  }
}
