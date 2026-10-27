package com.example.short_link.post.application.access;

import com.example.short_link.common.post.PostAccessSql;
import com.example.short_link.common.post.PostAudience;
import com.example.short_link.common.post.PostLock;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostHighlightEntity;
import com.example.short_link.post.domain.repository.PostRoomReader;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import com.example.short_link.user.application.read.FollowingReader;
import com.example.short_link.user.domain.FollowEdge;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// Who may read a post, for one post in Java; PostAccessSql says the same for lists in SQL. Each
// answer is one of PostAudience's questions, asked of the reader in hand.
@Component
@RequiredArgsConstructor
public class PostAccessPolicy {

  private final FollowingReader follows;
  private final PostGrants grants;
  private final ReaderCountryResolver countries;
  private final PostRoomReader rooms;

  public PostAccess decide(PostEntity post, PostReader reader) {
    return decide(
        post,
        isOwner(post, reader),
        keyMatches(post, reader.postKey()),
        () -> countries.resolve(reader),
        () -> follows(reader.viewerId(), post.getUserId()),
        () -> grants.verify(post, reader.grant()),
        () -> rooms.isMember(post.getRoomId(), reader.viewerId()));
  }

  public boolean readable(PostEntity post, PostReader reader) {
    PostAccess decided = decide(post, reader);
    if (decided.redacted()) {
      throw new PostException(PostErrorCode.POST_LOCKED, post.getId())
          .with("reason", decided.lock());
    }
    return decided.full();
  }

  public PostEntity requireReadable(PostEntity post, PostReader reader) {
    if (!readable(post, reader)) {
      throw new PostException(PostErrorCode.POST_NOT_FOUND, post.getId());
    }
    return post;
  }

  public Lists forLists(PostReader reader) {
    return new Lists(reader.withoutCredentials());
  }

  public boolean revealsExistence(PostEntity post, PostReader reader) {
    return post.getAudience().shownAsCard()
        || isOwner(post, reader)
        || keyMatches(post, reader.postKey())
        || (post.isInRoom() && rooms.isMember(post.getRoomId(), reader.viewerId()));
  }

  // Of the people a post would tell about something said on it, those who may read it. Only a room
  // narrows this: every other audience shows anyone at least its card.
  public Set<Long> readersAmong(PostEntity post, Collection<Long> userIds) {
    if (!post.isInRoom()) {
      return Set.copyOf(userIds);
    }
    Set<Long> readers = new HashSet<>(rooms.membersAmong(post.getRoomId(), userIds));
    if (userIds.contains(post.getUserId())) {
      readers.add(post.getUserId());
    }
    return readers;
  }

  // A comment or highlight made while its post was in a room stays with that room's members, its
  // writer too only while in the room; PostAccessSql.said asks the same for lists.
  public boolean hears(Long saidInRoom, Long viewerId) {
    return saidInRoom == null || rooms.isMember(saidInRoom, viewerId);
  }

  // What may stand on its own outside the post (a collection, a shared feed): never something said
  // in a room. PostAccessSql.saidOpenly asks the same for lists.
  public static boolean saidOpenly(PostHighlightEntity highlight) {
    return highlight.getRoomId() == null;
  }

  static PostAccess decide(
      PostEntity post,
      boolean owner,
      boolean rightKey,
      Supplier<ReaderCountry> country,
      BooleanSupplier follower,
      BooleanSupplier validGrant,
      BooleanSupplier member) {
    if (!post.isPublished()) return PostAccess.HIDDEN;
    if (owner) return PostAccess.FULL;
    PostAudience audience = post.getAudience();
    boolean reads =
        audience.readableBy(
            reader ->
                switch (reader) {
                  case ANYONE -> true;
                  case KEY_HOLDER -> rightKey;
                  case FOLLOWER -> follower.getAsBoolean();
                  case GRANT_HOLDER -> validGrant.getAsBoolean();
                  case MEMBER -> member.getAsBoolean();
                });
    if (!reads && !audience.shownAsCard()) return PostAccess.HIDDEN;
    if (post.isRegionRestricted()) {
      ReaderCountry reader = country.get();
      if (reader.deferred()) return PostAccess.locked(PostLock.REGION_CHECK);
      if (post.blocksCountry(reader.code())) return PostAccess.locked(PostLock.REGION);
    }
    return reads ? PostAccess.FULL : PostAccess.locked(audience.lock());
  }

  public static PostAccess card(PostEntity post) {
    if (!isCard(post)) return PostAccess.HIDDEN;
    PostLock lock = post.getAudience().lock();
    if (lock != null) return PostAccess.locked(lock);
    return post.isRegionRestricted() ? PostAccess.redactedCard() : PostAccess.FULL;
  }

  public static boolean isListed(PostEntity post) {
    return post.isPublished()
        && post.getAudience().listed()
        && (PostAccessSql.REGION_RESTRICTED_IN_DISCOVERY || !post.isRegionRestricted());
  }

  public static boolean isOpen(PostEntity post) {
    return post.isPublished() && post.getAudience().listed() && !post.isRegionRestricted();
  }

  public static boolean isCard(PostEntity post) {
    return post.isPublished() && post.getAudience().shownAsCard();
  }

  private static boolean isOwner(PostEntity post, PostReader reader) {
    return reader.viewerId() != null && post.isOwnedBy(reader.viewerId());
  }

  private static boolean keyMatches(PostEntity post, String key) {
    String expected = post.getUnlistedKey();
    return key != null
        && expected != null
        && MessageDigest.isEqual(
            key.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
  }

  private boolean follows(Long viewerId, Long authorId) {
    return viewerId != null && follows.follows(FollowEdge.follower(viewerId).following(authorId));
  }

  public final class Lists {

    private final PostReader reader;
    private final Map<Long, Boolean> followed = new HashMap<>();
    private ReaderCountry resolved;
    private Set<Long> memberOf;

    private Lists(PostReader reader) {
      this.reader = reader;
    }

    public PostAccess of(PostEntity post) {
      return decide(
          post,
          isOwner(post, reader),
          false,
          this::country,
          () ->
              followed.computeIfAbsent(
                  post.getUserId(), author -> follows(reader.viewerId(), author)),
          () -> false,
          () -> memberOf().contains(post.getRoomId()));
    }

    // What a list of the reader's own (likes, bookmarks, reading history) shows for a post: its
    // card, and a room post only while the reader is in the room.
    public PostAccess card(PostEntity post) {
      return post.isInRoom() ? of(post) : PostAccessPolicy.card(post);
    }

    // Whether the reader's own comments and highlights on a post still name it: always, but for a
    // room post only while the reader is in the room.
    public boolean keeps(PostEntity post) {
      return !post.isInRoom() || !of(post).hidden();
    }

    private Set<Long> memberOf() {
      if (memberOf == null) memberOf = rooms.roomsOf(reader.viewerId());
      return memberOf;
    }

    private ReaderCountry country() {
      if (resolved == null) resolved = Objects.requireNonNull(countries.resolve(reader));
      return resolved;
    }
  }
}
