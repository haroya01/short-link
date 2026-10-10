package com.example.short_link.user.application.read;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.user.domain.FollowEntity;
import com.example.short_link.user.domain.FollowRequestEntity;
import com.example.short_link.user.domain.UserBlockEntity;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserMuteEntity;
import com.example.short_link.user.domain.repository.BlockRepository;
import com.example.short_link.user.domain.repository.FollowRepository;
import com.example.short_link.user.domain.repository.FollowRequestRepository;
import com.example.short_link.user.domain.repository.MuteRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserSearchIntegrationTest {

  @Autowired private UserSearchService search;
  @Autowired private UserRepository userRepository;
  @Autowired private FollowRepository followRepository;
  @Autowired private FollowRequestRepository followRequestRepository;
  @Autowired private BlockRepository blockRepository;
  @Autowired private MuteRepository muteRepository;

  private String token;
  private String followedName;
  private String displayName;
  private long viewer;

  @BeforeEach
  void people() {
    token = "q" + UUID.randomUUID().toString().replace("-", "").substring(0, 9);
    followedName = "x" + token;
    displayName = "d" + token;
    viewer = user("v" + token, u -> {}).getId();
    Instant now = Instant.now();

    user(token, u -> {});
    long followed = user(followedName, u -> u.updateDisplayName("Fan of " + token)).getId();
    long big = user(token + "big", u -> {}).getId();
    user(token + "aaa", u -> u.updateBio("  first line\n\nsecond   line  "));
    user(token + "bbb", u -> {});
    user(displayName, u -> u.updateDisplayName("Mr " + token.toUpperCase()));
    user(token + "del", UserEntity::softDelete);
    user(token + "ban", UserEntity::ban);
    user(token + "sus", u -> u.suspend(now.plus(1, ChronoUnit.DAYS)));
    user(token + "old", u -> u.suspend(now.minus(1, ChronoUnit.DAYS)));
    user(null, u -> u.updateDisplayName("Nameless " + token));
    long blocked = user(token + "blk", u -> {}).getId();
    long blocker = user(token + "blr", u -> {}).getId();
    long muted = user(token + "mut", u -> {}).getId();
    long locked =
        user(
                token + "lck",
                u -> {
                  u.updateLocked(true);
                  u.updateHideFollowerCount(true);
                })
            .getId();

    followRepository.save(new FollowEntity(viewer, followed));
    followRepository.save(new FollowEntity(user("f1" + token, u -> {}).getId(), big));
    followRepository.save(new FollowEntity(user("f2" + token, u -> {}).getId(), big));
    followRepository.save(
        new FollowEntity(user("f3" + token, UserEntity::softDelete).getId(), big));
    followRequestRepository.save(new FollowRequestEntity(viewer, locked));
    blockRepository.save(new UserBlockEntity(viewer, blocked));
    blockRepository.save(new UserBlockEntity(blocker, viewer));
    muteRepository.save(new UserMuteEntity(viewer, muted, true, null));
  }

  private UserEntity user(String username, Consumer<UserEntity> shape) {
    String id = UUID.randomUUID().toString();
    UserEntity user = new UserEntity(id + "@example.com", "google", id);
    if (username != null) {
      user.claimUsername(username);
    }
    shape.accept(user);
    return userRepository.save(user);
  }

  private List<String> names(UserSearchView view) {
    return view.items().stream().map(UserSearchView.Item::username).toList();
  }

  @Test
  void ranksExactThenFollowedThenHandlesThenDisplayNamesAndLeavesOutTheGoneAndBlocked() {
    UserSearchView view = search.search(viewer, "@" + token.toUpperCase(), 0, 20);

    assertThat(names(view))
        .containsExactly(
            token,
            followedName,
            token + "big",
            token + "aaa",
            token + "bbb",
            token + "lck",
            token + "mut",
            token + "old",
            displayName);
    assertThat(view.hasNext()).isFalse();
  }

  @Test
  void anonymousSeesBlockedPeopleAndOrdersEachMatchKindByFollowers() {
    UserSearchView view = search.search(null, token, 0, 20);

    assertThat(names(view))
        .containsExactly(
            token,
            token + "big",
            token + "aaa",
            token + "bbb",
            token + "blk",
            token + "blr",
            token + "lck",
            token + "mut",
            token + "old",
            followedName,
            displayName);
    assertThat(view.items()).noneMatch(i -> i.following() || i.requested());
  }

  @Test
  void itemsCarryLiveFollowerCountsHiddenCountsAndTheViewersFollowState() {
    List<UserSearchView.Item> items = search.search(viewer, token, 0, 20).items();

    UserSearchView.Item big = items.get(2);
    assertThat(big.username()).isEqualTo(token + "big");
    assertThat(big.followerCount()).isEqualTo(2L);
    assertThat(big.following()).isFalse();
    UserSearchView.Item locked = items.get(5);
    assertThat(locked.username()).isEqualTo(token + "lck");
    assertThat(locked.followerCount()).isNull();
    assertThat(locked.requested()).isTrue();
    assertThat(items.get(1).following()).isTrue();
    assertThat(items.get(1).displayName()).isEqualTo("Fan of " + token);
    assertThat(items.get(3).bio()).isEqualTo("first line second line");
  }

  @Test
  void pagesCountFromTheRankedListAndSayWhetherMoreFollow() {
    UserSearchView first = search.search(viewer, token, 0, 4);
    UserSearchView last = search.search(viewer, token, 2, 4);

    assertThat(names(first)).containsExactly(token, followedName, token + "big", token + "aaa");
    assertThat(first.hasNext()).isTrue();
    assertThat(names(last)).containsExactly(displayName);
    assertThat(last.hasNext()).isFalse();
  }

  @Test
  void wildcardsMatchOnlyThemselves() {
    assertThat(search.search(viewer, "_" + token.substring(1), 0, 20).items()).isEmpty();
    assertThat(search.search(viewer, token.substring(0, 3) + "%big", 0, 20).items()).isEmpty();
  }
}
