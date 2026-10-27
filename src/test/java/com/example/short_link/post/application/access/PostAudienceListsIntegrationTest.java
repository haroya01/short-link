package com.example.short_link.post.application.access;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.common.post.PostAccessSql;
import com.example.short_link.common.post.PostAudience;
import com.example.short_link.common.post.PostLock;
import com.example.short_link.common.post.PublishedPostCountReader;
import com.example.short_link.post.application.read.PostHighlightQueryService;
import com.example.short_link.post.application.read.PostLikeQueryService;
import com.example.short_link.post.application.read.PostLockView;
import com.example.short_link.post.application.read.PublicFeedItem;
import com.example.short_link.post.application.read.PublicFeedQuery;
import com.example.short_link.post.application.read.PublicFeedQueryService;
import com.example.short_link.post.application.read.PublicPostListItem;
import com.example.short_link.post.application.read.PublicPostQueryService;
import com.example.short_link.post.application.read.ReadingHistoryQueryService;
import com.example.short_link.post.application.read.SavedLibraryQueryService;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostHighlightEntity;
import com.example.short_link.post.domain.PostReadEntity;
import com.example.short_link.post.domain.repository.PostBookmarkRepository;
import com.example.short_link.post.domain.repository.PostHighlightRepository;
import com.example.short_link.post.domain.repository.PostLikeRepository;
import com.example.short_link.post.domain.repository.PostReadRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.support.DiscoverableBodies;
import com.example.short_link.user.domain.FollowEdge;
import com.example.short_link.user.domain.FollowEntity;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.FollowRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PostAudienceListsIntegrationTest {

  private static final String TOPIC = "audtopic";
  private static final String IN_GB = "81.2.69.142";

  @Autowired private PostRepository posts;
  @Autowired private UserRepository users;
  @Autowired private FollowRepository follows;
  @Autowired private PostLikeRepository likes;
  @Autowired private PostBookmarkRepository bookmarks;
  @Autowired private PostReadRepository reads;
  @Autowired private PostHighlightRepository highlights;
  @Autowired private PublicFeedQueryService feed;
  @Autowired private PublicPostQueryService profile;
  @Autowired private SavedLibraryQueryService saved;
  @Autowired private PostLikeQueryService liked;
  @Autowired private ReadingHistoryQueryService history;
  @Autowired private PostHighlightQueryService highlightFeed;
  @Autowired private PublishedPostCountReader counts;
  @Autowired private EntityManager em;

  private UserEntity author;
  private long reader;
  private long follower;
  private long member;
  private long room;
  private final Map<String, PostEntity> byName = new LinkedHashMap<>();

  @BeforeEach
  void anAuthorWithOnePostOfEveryKind() {
    author = user("aud-author");
    reader = user("aud-reader").getId();
    follower = user("aud-follower").getId();
    follows.save(new FollowEntity(FollowEdge.follower(follower).following(author.getId())));
    follows.save(new FollowEntity(FollowEdge.follower(reader).following(author.getId())));
    member = user("aud-member").getId();
    room = room(author.getId(), member);
    for (PostAudience audience : PostAudience.values()) {
      for (boolean blocked : List.of(false, true)) {
        String name = audience.name().toLowerCase() + (blocked ? "-gb" : "");
        byName.put(name, post(name, audience, blocked, true));
      }
    }
    byName.put("draft", post("draft", PostAudience.PUBLIC, false, false));
    em.flush();
  }

  @Test
  void everySqlFragmentKeepsExactlyThePostsItsJavaTwinKeeps() {
    assertSameAs(PostAccessSql.LISTED, PostAccessPolicy::isListed, null);
    assertSameAs(PostAccessSql.OPEN, PostAccessPolicy::isOpen, null);
    assertSameAs(PostAccessSql.CARD, PostAccessPolicy::isCard, null);
    assertSameAs(PostAccessSql.PROFILE, post -> PostAccessPolicy.isCard(post), reader);
    assertSameAs(
        PostAccessSql.PROFILE,
        post -> post.isPublished() && post.getAudience().onProfile(true),
        author.getId());
  }

  @Test
  void discoveryListsOnlyPublicPostsAndRedactsTheCountryRestrictedOne() {
    for (String sort : List.of("recent", "trending")) {
      Map<String, PublicFeedItem> browsed =
          bySlug(feed.feed(null, PublicFeedQuery.from(null, null, sort, null, 0, 100)).items());
      Map<String, PublicFeedItem> tagged =
          bySlug(feed.feed(null, PublicFeedQuery.from(null, TOPIC, sort, null, 0, 100)).items());
      Map<String, PublicFeedItem> searched =
          bySlug(
              feed.feed(null, PublicFeedQuery.from("aud-author", null, sort, null, 0, 100))
                  .items());
      for (Map<String, PublicFeedItem> list : List.of(browsed, tagged, searched)) {
        assertThat(list.keySet()).containsExactlyInAnyOrder("public", "public-gb");
        assertThat(list.get("public").excerpt()).isNotNull();
        assertRedactedCard(list.get("public-gb"), null);
      }
    }
    assertThat(feed.popularTags(50)).anySatisfy(tag -> assertThat(tag.tag()).isEqualTo(TOPIC));
    assertThat(
            feed.popularTags(50).stream()
                .filter(t -> t.tag().equals(TOPIC))
                .findFirst()
                .get()
                .count())
        .isEqualTo(2);
    assertThat(feed.suggestedAuthors(null, 50))
        .filteredOn(s -> s.author().username().equals("aud-author"))
        .singleElement()
        .satisfies(s -> assertThat(s.postCount()).isEqualTo(2));
  }

  @Test
  void theFollowingFeedCarriesOnlyWhatDiscoveryWould() {
    assertThat(bySlug(feed.feedFollowing(follower, 0, 100).items()).keySet())
        .containsExactlyInAnyOrder("public", "public-gb");
  }

  @Test
  void theProfileListShowsTitlesPerReaderAndTheUnlistedOnlyToItsAuthor() {
    Map<String, PublicPostListItem> anonymousInGb =
        profileList(new PostViewer(null, null, null, IN_GB, false));
    assertThat(anonymousInGb.keySet())
        .containsExactlyInAnyOrder(
            "public", "public-gb", "followers", "followers-gb", "password", "password-gb");
    assertThat(lock(anonymousInGb.get("public-gb"))).isEqualTo(PostLock.REGION);
    assertThat(lock(anonymousInGb.get("followers"))).isEqualTo(PostLock.FOLLOWERS);
    assertThat(lock(anonymousInGb.get("password-gb"))).isEqualTo(PostLock.REGION);
    assertThat(anonymousInGb.get("followers").excerpt()).isNull();
    assertThat(anonymousInGb.get("followers").tags()).isEmpty();
    assertThat(anonymousInGb.get("public").excerpt()).isNotNull();

    Map<String, PublicPostListItem> followerElsewhere = profileList(PostViewer.of(follower));
    assertThat(lock(followerElsewhere.get("followers"))).isNull();
    assertThat(lock(followerElsewhere.get("followers-gb"))).isNull();
    assertThat(lock(followerElsewhere.get("password"))).isEqualTo(PostLock.PASSWORD);
    assertThat(followerElsewhere.get("public-gb").regionRestricted()).isTrue();
    assertThat(followerElsewhere.get("public-gb").blockedCountries()).isNull();

    Map<String, PublicPostListItem> owner = profileList(PostViewer.of(author.getId()));
    assertThat(owner.keySet())
        .contains("unlisted", "unlisted-gb")
        .doesNotContain("draft", "room", "room-gb");
    assertThat(profileList(PostViewer.of(member)).keySet()).doesNotContain("room", "room-gb");
    assertThat(owner.values()).allSatisfy(item -> assertThat(item.locked()).isNull());
    assertThat(owner.get("unlisted").unlistedUrl())
        .isEqualTo(
            "https://blog.kurl.me/@aud-author/unlisted?k="
                + byName.get("unlisted").getUnlistedKey());
    assertThat(owner.get("password").hasPassword()).isTrue();
    assertThat(owner.get("public-gb").blockedCountries()).containsExactly("GB");
    assertThat(owner.get("public").blockedCountries()).isEmpty();

    assertThat(counts.countPublishedByUserId(author.getId())).isEqualTo(6);
  }

  @Test
  void savedLikedAndReadPostsDropTheUnlistedAndRedactTheRest() {
    for (PostEntity post : byName.values()) {
      likes.insertIgnore(post.getId(), reader);
      bookmarks.insertIgnore(post.getId(), reader);
      reads.save(new PostReadEntity(reader, post.getId(), Instant.now()));
    }
    em.flush();

    List<String> cards =
        List.of("public", "public-gb", "followers", "followers-gb", "password", "password-gb");
    Map<String, PublicFeedItem> likedCards = bySlug(liked.likedPosts(reader));
    assertThat(likedCards.keySet()).containsExactlyInAnyOrderElementsOf(cards);
    assertRedactedCard(likedCards.get("followers"), PostLock.FOLLOWERS);
    assertRedactedCard(likedCards.get("password-gb"), PostLock.PASSWORD);
    assertRedactedCard(likedCards.get("public-gb"), null);
    assertThat(likedCards.get("public").excerpt()).isEqualTo("excerpt of public");

    assertThat(saved.saved(reader))
        .extracting(s -> s.slug())
        .containsExactlyInAnyOrderElementsOf(cards);
    assertThat(saved.saved(reader))
        .filteredOn(s -> s.slug().equals("followers-gb"))
        .singleElement()
        .satisfies(
            s -> {
              assertThat(s.excerpt()).isNull();
              assertThat(s.locked().reason()).isEqualTo(PostLock.FOLLOWERS);
            });

    assertThat(history.list(reader, 0, 50).items())
        .extracting(entry -> entry.slug())
        .containsExactlyInAnyOrderElementsOf(cards);
    assertThat(history.list(reader, 0, 50).items())
        .filteredOn(entry -> entry.slug().equals("password"))
        .singleElement()
        .satisfies(entry -> assertThat(entry.excerpt()).isNull());
  }

  @Test
  void aRoomPostIsInItsMembersOwnListsAndNoOneElses() {
    for (long who : List.of(member, reader)) {
      for (String name : List.of("room", "public")) {
        likes.insertIgnore(byName.get(name).getId(), who);
        bookmarks.insertIgnore(byName.get(name).getId(), who);
        reads.save(new PostReadEntity(who, byName.get(name).getId(), Instant.now()));
      }
    }
    em.flush();

    assertThat(bySlug(liked.likedPosts(member)).keySet())
        .containsExactlyInAnyOrder("room", "public");
    assertThat(bySlug(liked.likedPosts(member)).get("room").room().id()).isEqualTo(room);
    assertThat(saved.saved(member)).extracting(s -> s.slug()).contains("room");
    assertThat(history.list(member, 0, 50).items())
        .extracting(entry -> entry.slug())
        .contains("room");

    assertThat(bySlug(liked.likedPosts(reader)).keySet()).containsExactly("public");
    assertThat(saved.saved(reader)).extracting(s -> s.slug()).doesNotContain("room");
    assertThat(history.list(reader, 0, 50).items())
        .extracting(entry -> entry.slug())
        .doesNotContain("room");
  }

  @Test
  void theHighlightFeedQuotesOnlyPostsAnyoneCanRead() {
    for (PostEntity post : byName.values()) {
      highlights.save(new PostHighlightEntity(post.getId(), reader, 0, 0, 0, 5, "quote", null));
    }
    em.flush();

    Set<Long> quoted =
        highlightFeed.feed(reader, 0, 100, true).items().stream()
            .map(item -> item.postId())
            .collect(Collectors.toSet());

    assertThat(quoted).contains(byName.get("public").getId());
    assertThat(quoted)
        .doesNotContain(
            byName.values().stream()
                .filter(post -> !PostAccessPolicy.isOpen(post))
                .map(PostEntity::getId)
                .toArray(Long[]::new));
  }

  private void assertSameAs(String fragment, Predicate<PostEntity> twin, Long viewer) {
    var query =
        em.createNativeQuery("SELECT p.id FROM posts p WHERE p.user_id = :author AND " + fragment)
            .setParameter("author", author.getId());
    if (fragment.contains(":viewer")) query.setParameter("viewer", viewer);
    @SuppressWarnings("unchecked")
    List<Number> kept = query.getResultList();
    Set<Long> sql = kept.stream().map(Number::longValue).collect(Collectors.toSet());
    Set<Long> java =
        byName.values().stream().filter(twin).map(PostEntity::getId).collect(Collectors.toSet());
    assertThat(sql).as(fragment).isEqualTo(java);
  }

  private void assertRedactedCard(PublicFeedItem card, PostLock lock) {
    assertThat(card.excerpt()).isNull();
    assertThat(card.ogImageUrl()).isNull();
    assertThat(card.thumbnailUrl()).isNull();
    assertThat(card.tags()).isEmpty();
    assertThat(card.likeCount()).isZero();
    assertThat(card.locked() == null ? null : card.locked().reason()).isEqualTo(lock);
  }

  private Map<String, PublicPostListItem> profileList(PostViewer viewer) {
    return profile.listPublicPosts("aud-author", viewer).body().posts().stream()
        .collect(Collectors.toMap(PublicPostListItem::slug, item -> item));
  }

  private static PostLock lock(PublicPostListItem item) {
    PostLockView locked = item.locked();
    return locked == null ? null : locked.reason();
  }

  private Map<String, PublicFeedItem> bySlug(List<PublicFeedItem> items) {
    Map<String, PublicFeedItem> out = new LinkedHashMap<>();
    for (PublicFeedItem item : items) {
      if (item.author().id().equals(author.getId())) out.put(item.slug(), item);
    }
    return out;
  }

  private PostEntity post(String slug, PostAudience audience, boolean blocked, boolean publish) {
    PostEntity post =
        DiscoverableBodies.discoverable(new PostEntity(author.getId(), slug, slug, "ko"));
    post.updateExcerpt("excerpt of " + slug);
    post.updateTags(List.of(TOPIC));
    if (publish) post.publish();
    post.protectWith("$2a$10$abcdefghijklmnopqrstuuvwxyzABCDEFGHIJKLMNOPQRSTUVWXY");
    post.changeAudience(audience, audience.inRoom() ? room : null, "key-" + slug);
    post.blockCountries(blocked ? List.of("GB") : List.of());
    return posts.save(post);
  }

  private long room(long owner, long otherMember) {
    em.createNativeQuery(
            "INSERT INTO room (owner_id, name, approval_required, created_at, updated_at)"
                + " VALUES (:owner, 'aud room', FALSE, NOW(6), NOW(6))")
        .setParameter("owner", owner)
        .executeUpdate();
    long id =
        ((Number) em.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult()).longValue();
    for (long who : List.of(owner, otherMember)) {
      em.createNativeQuery(
              "INSERT INTO room_member (room_id, user_id, joined_at) VALUES (:room, :user, NOW(6))")
          .setParameter("room", id)
          .setParameter("user", who)
          .executeUpdate();
    }
    return id;
  }

  private UserEntity user(String handle) {
    UserEntity user = new UserEntity(handle + "@example.com", "google", handle);
    user.claimUsername(handle);
    return users.save(user);
  }
}
