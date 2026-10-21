package com.example.short_link.post.application.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.common.geoip.GeoLocation;
import com.example.short_link.common.post.PostAudience;
import com.example.short_link.common.post.PostLock;
import com.example.short_link.link.classifier.application.GeoIpResolver;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRoomReader;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import com.example.short_link.support.TestEntities;
import com.example.short_link.user.domain.repository.FollowRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PostAccessPolicyTest {

  private static final long AUTHOR = 1L;
  private static final long FOLLOWER = 2L;
  private static final long PENDING = 3L;
  private static final long STRANGER = 4L;
  private static final long MEMBER = 5L;
  private static final long ROOM = 77L;
  private static final String BLOCKED_HERE = "JP";

  private enum Viewer {
    ANONYMOUS,
    NON_FOLLOWER,
    FOLLOWER,
    PENDING_FOLLOWER,
    OWNER,
    GRANT_HOLDER,
    WRONG_KEY,
    RIGHT_KEY,
    MEMBER
  }

  private static final List<Viewer> COLUMNS = List.of(Viewer.values());

  private static final String[][] TABLE = {
    {"PUBLIC", "open", "F F F F F F F F F"},
    {"UNLISTED", "open", "H H H H F H H F H"},
    {"FOLLOWERS", "open", "Lf Lf F Lf F Lf Lf Lf Lf"},
    {"PASSWORD", "open", "Lp Lp Lp Lp F F Lp Lp Lp"},
    {"ROOM", "open", "H H H H F H H H F"},
    {"PUBLIC", "blocked", "Lr Lr Lr Lr F Lr Lr Lr Lr"},
    {"UNLISTED", "blocked", "H H H H F H H Lr H"},
    {"FOLLOWERS", "blocked", "Lr Lr Lr Lr F Lr Lr Lr Lr"},
    {"PASSWORD", "blocked", "Lr Lr Lr Lr F Lr Lr Lr Lr"},
    {"ROOM", "blocked", "H H H H F H H H Lr"},
  };

  private final FollowRepository follows = mock(FollowRepository.class);
  private final GeoIpResolver geo = mock(GeoIpResolver.class);
  private final PostGrants grants =
      new PostGrants(
          "signing-secret", Clock.fixed(Instant.parse("2026-10-16T00:00:00Z"), ZoneOffset.UTC));
  private final PostRoomReader rooms = mock(PostRoomReader.class);
  private final PostAccessPolicy policy =
      new PostAccessPolicy(follows, grants, new ReaderCountryResolver(geo), rooms);

  static Stream<Arguments> table() {
    List<Arguments> cells = new ArrayList<>();
    for (String[] row : TABLE) {
      String[] expected = row[2].split(" ");
      for (int i = 0; i < COLUMNS.size(); i++) {
        cells.add(
            Arguments.of(
                PostAudience.valueOf(row[0]),
                "blocked".equals(row[1]),
                COLUMNS.get(i),
                expected[i]));
      }
    }
    return cells.stream();
  }

  @ParameterizedTest(name = "{0} {1} as {2} -> {3}")
  @MethodSource("table")
  void everyReaderGetsWhatTheTableSays(
      PostAudience audience, boolean countryBlocked, Viewer viewer, String expected) {
    when(geo.resolve(any())).thenReturn(new GeoLocation(BLOCKED_HERE, null, null));
    when(follows.existsByFollowerIdAndFollowingId(FOLLOWER, AUTHOR)).thenReturn(true);
    when(rooms.isMember(ROOM, MEMBER)).thenReturn(true);
    PostEntity post = post(audience, countryBlocked ? List.of(BLOCKED_HERE) : List.of());

    assertThat(code(policy.decide(post, reader(viewer, post)))).isEqualTo(expected);
  }

  @ParameterizedTest(name = "{0} {1} as {2}")
  @MethodSource("table")
  void aListNeverTakesKeysOrGrants(
      PostAudience audience, boolean countryBlocked, Viewer viewer, String ignored) {
    when(geo.resolve(any())).thenReturn(new GeoLocation(BLOCKED_HERE, null, null));
    when(follows.existsByFollowerIdAndFollowingId(FOLLOWER, AUTHOR)).thenReturn(true);
    when(rooms.isMember(ROOM, MEMBER)).thenReturn(true);
    when(rooms.roomsOf(MEMBER)).thenReturn(Set.of(ROOM));
    PostEntity post = post(audience, countryBlocked ? List.of(BLOCKED_HERE) : List.of());
    PostReader reader = reader(viewer, post);

    assertThat(code(policy.forLists(reader).of(post)))
        .isEqualTo(code(policy.decide(post, reader.withoutCredentials())));
  }

  @Test
  void aCountryElsewhereOnTheListDoesNotLockThisReader() {
    when(geo.resolve(any())).thenReturn(new GeoLocation("KR", null, null));
    PostEntity post = post(PostAudience.PUBLIC, List.of(BLOCKED_HERE));

    assertThat(policy.decide(post, reader(Viewer.ANONYMOUS, post)).full()).isTrue();
  }

  @Test
  void anUnknownCountryPassesAsItDoesForLinks() {
    when(geo.resolve(any())).thenReturn(GeoLocation.empty());
    PostEntity post = post(PostAudience.FOLLOWERS, List.of(BLOCKED_HERE));
    when(follows.existsByFollowerIdAndFollowingId(FOLLOWER, AUTHOR)).thenReturn(true);

    assertThat(policy.decide(post, reader(Viewer.FOLLOWER, post)).full()).isTrue();
  }

  @Test
  void aServerRenderOfARegionRestrictedPostIsSentBackToTheBrowser() {
    PostEntity post = post(PostAudience.PUBLIC, List.of(BLOCKED_HERE));
    PostReader render = new PostReader(null, null, null, "76.76.21.21", true);

    assertThat(policy.decide(post, render).lock()).isEqualTo(PostLock.REGION_CHECK);
    verifyNoInteractions(geo);
  }

  @Test
  void aServerRenderOfAnUnrestrictedPostIsAnsweredInFull() {
    PostEntity post = post(PostAudience.PUBLIC, List.of());

    assertThat(policy.decide(post, new PostReader(null, null, null, null, true)).full()).isTrue();
  }

  @Test
  void anUnpublishedPostIsHiddenEvenFromItsAuthor() {
    PostEntity draft = TestEntities.withId(new PostEntity(AUTHOR, "d", "Draft", "ko"), 9L);

    assertThat(policy.decide(draft, PostReader.of(AUTHOR)).hidden()).isTrue();
  }

  @Test
  void aPublicPostAsksNothingOfTheDatabaseOrGeoIp() {
    PostEntity post = post(PostAudience.PUBLIC, List.of());

    assertThat(policy.decide(post, reader(Viewer.NON_FOLLOWER, post)).full()).isTrue();
    verifyNoInteractions(follows, geo);
  }

  @Test
  void aNewPasswordEndsEveryGrantIssuedBeforeIt() {
    PostEntity post = post(PostAudience.PASSWORD, List.of());
    String grant = grants.issue(post).grant();
    post.protectWith("$2a$10$another-password-hash-0000000000000000000000000000000");

    assertThat(policy.decide(post, new PostReader(STRANGER, null, grant, null, false)).lock())
        .isEqualTo(PostLock.PASSWORD);
  }

  @Test
  void anExpiredGrantNoLongerOpensThePost() {
    PostEntity post = post(PostAudience.PASSWORD, List.of());
    String grant = grants.issue(post).grant();
    PostGrants later =
        new PostGrants(
            "signing-secret", Clock.fixed(Instant.parse("2026-10-17T00:00:01Z"), ZoneOffset.UTC));

    assertThat(later.verify(post, grant)).isFalse();
    assertThat(later.verify(post, "not-a-grant")).isFalse();
    assertThat(later.verify(post, "123.abc")).isFalse();
  }

  @Test
  void aGrantForOnePostDoesNotOpenAnother() {
    PostEntity post = post(PostAudience.PASSWORD, List.of());
    PostEntity other = TestEntities.withId(post(PostAudience.PASSWORD, List.of()), 43L);

    assertThat(grants.verify(other, grants.issue(post).grant())).isFalse();
  }

  @Test
  void readableHidesWhatIsHiddenAndRefusesWhatIsLocked() {
    PostEntity unlisted = post(PostAudience.UNLISTED, List.of());
    PostEntity followers = post(PostAudience.FOLLOWERS, List.of());

    assertThat(policy.readable(unlisted, PostReader.of(STRANGER))).isFalse();
    assertThatThrownBy(() -> policy.requireReadable(unlisted, PostReader.of(STRANGER)))
        .isInstanceOfSatisfying(
            PostException.class,
            e -> assertThat(e.errorCode()).isEqualTo(PostErrorCode.POST_NOT_FOUND));
    assertThatThrownBy(() -> policy.readable(followers, PostReader.of(STRANGER)))
        .isInstanceOfSatisfying(
            PostException.class,
            e -> {
              assertThat(e.errorCode()).isEqualTo(PostErrorCode.POST_LOCKED);
              assertThat(e.properties()).containsEntry("reason", PostLock.FOLLOWERS);
            });
  }

  @Test
  void cardsAreTheSameForEveryViewer() {
    assertThat(PostAccessPolicy.card(post(PostAudience.PUBLIC, List.of())).full()).isTrue();
    assertThat(PostAccessPolicy.card(post(PostAudience.UNLISTED, List.of())).hidden()).isTrue();
    assertThat(PostAccessPolicy.card(post(PostAudience.ROOM, List.of())).hidden()).isTrue();
    assertThat(PostAccessPolicy.card(post(PostAudience.FOLLOWERS, List.of())).lock())
        .isEqualTo(PostLock.FOLLOWERS);
    assertThat(PostAccessPolicy.card(post(PostAudience.PASSWORD, List.of())).lock())
        .isEqualTo(PostLock.PASSWORD);
    PostAccess region = PostAccessPolicy.card(post(PostAudience.PUBLIC, List.of(BLOCKED_HERE)));
    assertThat(region.redacted()).isTrue();
    assertThat(region.lock()).isNull();
  }

  @Test
  void onlyAnUnrestrictedPublicPostIsOpenAndOnlyPublicPostsAreListed() {
    assertThat(PostAccessPolicy.isOpen(post(PostAudience.PUBLIC, List.of()))).isTrue();
    assertThat(PostAccessPolicy.isOpen(post(PostAudience.PUBLIC, List.of(BLOCKED_HERE)))).isFalse();
    assertThat(PostAccessPolicy.isOpen(post(PostAudience.FOLLOWERS, List.of()))).isFalse();
    assertThat(PostAccessPolicy.isListed(post(PostAudience.PUBLIC, List.of(BLOCKED_HERE))))
        .isTrue();
    assertThat(PostAccessPolicy.isListed(post(PostAudience.PASSWORD, List.of()))).isFalse();
    assertThat(PostAccessPolicy.isCard(post(PostAudience.FOLLOWERS, List.of()))).isTrue();
    assertThat(PostAccessPolicy.isCard(post(PostAudience.UNLISTED, List.of()))).isFalse();
    assertThat(PostAccessPolicy.isOpen(post(PostAudience.ROOM, List.of()))).isFalse();
    assertThat(PostAccessPolicy.isListed(post(PostAudience.ROOM, List.of()))).isFalse();
    assertThat(PostAccessPolicy.isCard(post(PostAudience.ROOM, List.of()))).isFalse();
  }

  @Test
  void onlyAnUnlistedPostWithoutItsKeyHidesThatItExists() {
    PostEntity unlisted = post(PostAudience.UNLISTED, List.of());

    assertThat(policy.revealsExistence(unlisted, PostReader.of(STRANGER))).isFalse();
    assertThat(policy.revealsExistence(unlisted, PostReader.of(AUTHOR))).isTrue();
    assertThat(
            policy.revealsExistence(
                unlisted, new PostReader(null, unlisted.getUnlistedKey(), null, null, false)))
        .isTrue();
    assertThat(
            policy.revealsExistence(post(PostAudience.FOLLOWERS, List.of()), PostReader.of(null)))
        .isTrue();
  }

  @Test
  void aRoomPostTellsOnlyItsMembersAndAuthorThatItExists() {
    when(rooms.isMember(ROOM, MEMBER)).thenReturn(true);
    PostEntity room = post(PostAudience.ROOM, List.of());

    assertThat(policy.revealsExistence(room, PostReader.of(STRANGER))).isFalse();
    assertThat(policy.revealsExistence(room, PostReader.of(null))).isFalse();
    assertThat(policy.revealsExistence(room, PostReader.of(MEMBER))).isTrue();
    assertThat(policy.revealsExistence(room, PostReader.of(AUTHOR))).isTrue();
  }

  @Test
  void onlyARoomPostNarrowsWhoHearsAboutIt() {
    when(rooms.membersAmong(ROOM, List.of(MEMBER, STRANGER, AUTHOR))).thenReturn(Set.of(MEMBER));
    PostEntity room = post(PostAudience.ROOM, List.of());
    PostEntity followers = post(PostAudience.FOLLOWERS, List.of());

    assertThat(policy.readersAmong(room, List.of(MEMBER, STRANGER, AUTHOR)))
        .containsExactlyInAnyOrder(MEMBER, AUTHOR);
    assertThat(policy.readersAmong(followers, List.of(STRANGER))).containsExactly(STRANGER);
    verifyNoInteractions(follows);
  }

  @Test
  void whatWasSaidInARoomIsHeardOnlyThere() {
    when(rooms.isMember(ROOM, MEMBER)).thenReturn(true);

    assertThat(policy.hears(null, null)).isTrue();
    assertThat(policy.hears(ROOM, MEMBER)).isTrue();
    assertThat(policy.hears(ROOM, STRANGER)).isFalse();
    assertThat(policy.hears(ROOM, null)).isFalse();
  }

  private PostEntity post(PostAudience audience, List<String> blocked) {
    PostEntity post = TestEntities.withId(new PostEntity(AUTHOR, "p", "Title", "ko"), 42L);
    post.publish();
    post.protectWith("$2a$10$abcdefghijklmnopqrstuuvwxyzABCDEFGHIJKLMNOPQRSTUVWXY");
    post.changeAudience(
        audience, audience.inRoom() ? ROOM : null, "the-unlisted-key-of-this-post-00");
    post.blockCountries(blocked);
    return post;
  }

  private PostReader reader(Viewer viewer, PostEntity post) {
    String ip = "203.0.113.7";
    return switch (viewer) {
      case ANONYMOUS -> new PostReader(null, null, null, ip, false);
      case NON_FOLLOWER -> new PostReader(STRANGER, null, null, ip, false);
      case FOLLOWER -> new PostReader(FOLLOWER, null, null, ip, false);
      case PENDING_FOLLOWER -> new PostReader(PENDING, null, null, ip, false);
      case OWNER -> new PostReader(AUTHOR, null, null, ip, false);
      case GRANT_HOLDER -> new PostReader(STRANGER, null, grants.issue(post).grant(), ip, false);
      case WRONG_KEY -> new PostReader(STRANGER, "not-the-key", null, ip, false);
      case RIGHT_KEY -> new PostReader(null, "the-unlisted-key-of-this-post-00", null, ip, false);
      case MEMBER -> new PostReader(MEMBER, null, null, ip, false);
    };
  }

  private static String code(PostAccess access) {
    if (access.full()) return "F";
    if (access.hidden()) return "H";
    return switch (access.lock()) {
      case FOLLOWERS -> "Lf";
      case PASSWORD -> "Lp";
      case REGION -> "Lr";
      case REGION_CHECK -> "Lc";
    };
  }
}
