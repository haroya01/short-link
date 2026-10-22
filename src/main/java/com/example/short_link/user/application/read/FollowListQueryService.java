package com.example.short_link.user.application.read;

import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.FollowListReader;
import com.example.short_link.user.domain.repository.FollowListReader.Entry;
import com.example.short_link.user.domain.repository.FollowListReader.RemoteAccount;
import com.example.short_link.user.domain.repository.FollowListReader.Side;
import com.example.short_link.user.domain.repository.FollowRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Anonymous viewers have followedByMe=false. Hidden counts also hide pageable lists from non-owners
// (403), because enumerating the lists would reveal the counts.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FollowListQueryService {

  private final UserRepository userRepository;
  private final FollowRepository followRepository;
  private final FollowListReader followLists;

  public FollowListView followers(Long viewerId, String username, int page, int size) {
    UserEntity target = resolve(viewerId, username);
    List<Long> ids = followRepository.findFollowerIds(target.getId(), page, size);
    boolean hasNext =
        (long) (page + 1) * size < followRepository.countByFollowingId(target.getId());
    return new FollowListView(hydrate(viewerId, ids), page, size, hasNext);
  }

  public FollowListView following(Long viewerId, String username, int page, int size) {
    UserEntity target = resolve(viewerId, username);
    List<Long> ids = followRepository.findFollowingIds(target.getId(), page, size);
    boolean hasNext = (long) (page + 1) * size < followRepository.countByFollowerId(target.getId());
    return new FollowListView(hydrate(viewerId, ids), page, size, hasNext);
  }

  public MixedFollowListView followersWithRemote(
      Long viewerId, String username, int page, int size) {
    return mixed(viewerId, resolve(viewerId, username), Side.FOLLOWERS, page, size);
  }

  public MixedFollowListView followingWithRemote(
      Long viewerId, String username, int page, int size) {
    return mixed(viewerId, resolve(viewerId, username), Side.FOLLOWING, page, size);
  }

  private MixedFollowListView mixed(
      Long viewerId, UserEntity target, Side side, int page, int size) {
    int offset = (int) Math.min((long) page * size, Integer.MAX_VALUE);
    List<Entry> rows = followLists.page(target.getId(), side, viewerId, offset, size + 1);
    List<Entry> shown = rows.subList(0, Math.min(size, rows.size()));
    Map<Long, FollowUserView> locals =
        hydrate(viewerId, shown.stream().filter(row -> !row.remote()).map(Entry::id).toList())
            .stream()
            .collect(Collectors.toMap(FollowUserView::id, Function.identity()));
    Map<Long, RemoteAccount> remotes =
        followLists.remoteAccounts(
            shown.stream().filter(Entry::remote).map(Entry::id).toList(), viewerId);
    List<FollowListItem> items =
        shown.stream()
            .map(
                row ->
                    row.remote()
                        ? Optional.ofNullable(remotes.get(row.id()))
                            .<FollowListItem>map(FollowListItem.Remote::new)
                        : Optional.ofNullable(locals.get(row.id()))
                            .<FollowListItem>map(FollowListItem.Local::of))
            .flatMap(Optional::stream)
            .toList();
    return new MixedFollowListView(items, page, size, rows.size() > size);
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
