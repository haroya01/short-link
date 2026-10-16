package com.example.short_link.post.application.access;

import com.example.short_link.common.post.PostAccessSql;
import com.example.short_link.common.post.PostAudience;
import com.example.short_link.common.post.PostLock;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import com.example.short_link.user.domain.repository.FollowRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// Who may read a post, for one post in Java; PostAccessSql says the same for lists in SQL.
@Component
@RequiredArgsConstructor
public class PostAccessPolicy {

  private final FollowRepository follows;
  private final PostGrants grants;
  private final ReaderCountryResolver countries;

  public PostAccess decide(PostEntity post, PostReader reader) {
    return decide(
        post,
        isOwner(post, reader),
        keyMatches(post, reader.postKey()),
        () -> countries.resolve(reader),
        () -> follows(reader.viewerId(), post.getUserId()),
        () -> grants.verify(post, reader.grant()));
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
    return post.getAudience() != PostAudience.UNLISTED
        || isOwner(post, reader)
        || keyMatches(post, reader.postKey());
  }

  static PostAccess decide(
      PostEntity post,
      boolean owner,
      boolean rightKey,
      Supplier<ReaderCountry> country,
      BooleanSupplier follower,
      BooleanSupplier validGrant) {
    if (!post.isPublished()) return PostAccess.HIDDEN;
    if (owner) return PostAccess.FULL;
    PostAudience audience = post.getAudience();
    if (audience == PostAudience.UNLISTED && !rightKey) return PostAccess.HIDDEN;
    if (post.isRegionRestricted()) {
      ReaderCountry reader = country.get();
      if (reader.deferred()) return PostAccess.locked(PostLock.REGION_CHECK);
      if (post.blocksCountry(reader.code())) return PostAccess.locked(PostLock.REGION);
    }
    return switch (audience) {
      case FOLLOWERS ->
          follower.getAsBoolean() ? PostAccess.FULL : PostAccess.locked(PostLock.FOLLOWERS);
      case PASSWORD ->
          validGrant.getAsBoolean() ? PostAccess.FULL : PostAccess.locked(PostLock.PASSWORD);
      case PUBLIC, UNLISTED -> PostAccess.FULL;
    };
  }

  public static PostAccess card(PostEntity post) {
    if (!isCard(post)) return PostAccess.HIDDEN;
    PostLock lock = post.getAudience().lock();
    if (lock != null) return PostAccess.locked(lock);
    return post.isRegionRestricted() ? PostAccess.redactedCard() : PostAccess.FULL;
  }

  public static boolean isListed(PostEntity post) {
    return post.isPublished()
        && post.getAudience() == PostAudience.PUBLIC
        && (PostAccessSql.REGION_RESTRICTED_IN_DISCOVERY || !post.isRegionRestricted());
  }

  public static boolean isOpen(PostEntity post) {
    return post.isPublished()
        && post.getAudience() == PostAudience.PUBLIC
        && !post.isRegionRestricted();
  }

  public static boolean isCard(PostEntity post) {
    return post.isPublished() && post.getAudience() != PostAudience.UNLISTED;
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
    return viewerId != null && follows.existsByFollowerIdAndFollowingId(viewerId, authorId);
  }

  public final class Lists {

    private final PostReader reader;
    private final Map<Long, Boolean> followed = new HashMap<>();
    private ReaderCountry resolved;

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
          () -> false);
    }

    private ReaderCountry country() {
      if (resolved == null) resolved = Objects.requireNonNull(countries.resolve(reader));
      return resolved;
    }
  }
}
