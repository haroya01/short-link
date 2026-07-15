package com.example.short_link.user.application.read;

import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.FollowRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The followers / following <em>lists</em> on an author page (Medium-style). Public — works
 * unauthenticated; the per-row {@code followedByMe} just stays false for anonymous viewers. Each
 * page of edge ids is batch-hydrated in three passes (users, follower counts, the viewer's own
 * edges) to avoid N+1.
 *
 * <p>An author who hides their counts ({@code hideFollowerCount}) locks these lists to themselves:
 * a list is a countable number, so omitting the totals while leaving the lists pageable would let
 * anyone rebuild the hidden count. Everyone but the owner gets {@code FOLLOW_LIST_HIDDEN} (403).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FollowListQueryService {

  private final UserRepository userRepository;
  private final FollowRepository followRepository;

  /** Users who follow {@code username}, newest follower first. */
  public FollowListView followers(Long viewerId, String username, int page, int size) {
    UserEntity target = resolve(viewerId, username);
    List<Long> ids = followRepository.findFollowerIds(target.getId(), page, size);
    boolean hasNext =
        (long) (page + 1) * size < followRepository.countByFollowingId(target.getId());
    return new FollowListView(hydrate(viewerId, ids), page, size, hasNext);
  }

  /** Users {@code username} follows, most recently followed first. */
  public FollowListView following(Long viewerId, String username, int page, int size) {
    UserEntity target = resolve(viewerId, username);
    List<Long> ids = followRepository.findFollowingIds(target.getId(), page, size);
    boolean hasNext = (long) (page + 1) * size < followRepository.countByFollowerId(target.getId());
    return new FollowListView(hydrate(viewerId, ids), page, size, hasNext);
  }

  private UserEntity resolve(Long viewerId, String username) {
    UserEntity target =
        userRepository
            .findByUsername(username)
            .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
    if (target.isHideFollowerCount() && !target.getId().equals(viewerId)) {
      throw new UserException(UserErrorCode.FOLLOW_LIST_HIDDEN);
    }
    return target;
  }

  /**
   * ids(최신순)를 표시 가능한 작가 행으로. 삭제됐거나 아직 핸들을 안 정한(username null) 사용자는 목록에서 건너뛰되, 페이지 순서(최신순)는 유지한다.
   */
  private List<FollowUserView> hydrate(Long viewerId, List<Long> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    Map<Long, UserEntity> byId =
        userRepository.findAllByIdIn(ids).stream()
            .filter(u -> u.getUsername() != null && !u.isDeleted())
            .collect(Collectors.toMap(UserEntity::getId, u -> u));
    Map<Long, Long> followerCounts = followRepository.countFollowersByIdIn(byId.keySet());
    Set<Long> followedByViewer =
        viewerId == null
            ? Set.of()
            : new HashSet<>(followRepository.findFollowedAmong(viewerId, byId.keySet()));
    return ids.stream()
        .map(byId::get)
        .filter(Objects::nonNull)
        .map(
            u ->
                new FollowUserView(
                    u.getId(),
                    u.getUsername(),
                    u.getBio(),
                    u.getAvatarUrl(),
                    u.isHideFollowerCount() ? null : followerCounts.getOrDefault(u.getId(), 0L),
                    followedByViewer.contains(u.getId())))
        .toList();
  }
}
