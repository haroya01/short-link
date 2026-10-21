package com.example.short_link.post.application.write;

import com.example.short_link.common.geoip.CountryBlocklist;
import com.example.short_link.common.post.PostAudience;
import com.example.short_link.post.application.access.PostPasswords;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRoomReader;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostAccessSettings {

  private static final int MAX_BLOCKED_COUNTRIES = 85;
  private static final SecureRandom RANDOM = new SecureRandom();

  private final PostPasswords passwords;
  private final PostRoomReader rooms;

  public void apply(PostEntity post, PostAccessChange change) {
    PostAudience audience = audience(change.audience(), post.getAudience());
    boolean placing = change.audience() != null || change.roomId() != null;
    if (placing) {
      requireRoom(post, audience, change.roomId());
    }
    if (change.password() != null && !PostPasswords.acceptableLength(change.password())) {
      throw new PostException(PostErrorCode.POST_PASSWORD_LENGTH);
    }
    if (audience.needsPassword() && change.password() == null && !post.hasPassword()) {
      throw new PostException(PostErrorCode.POST_PASSWORD_REQUIRED);
    }
    List<String> blocked =
        change.blockedCountries() == null ? null : countries(change.blockedCountries());
    if (change.password() != null) {
      post.protectWith(passwords.hash(change.password()));
    }
    if (blocked != null) {
      post.blockCountries(blocked);
    }
    if (placing) {
      post.changeAudience(audience, change.roomId(), newUnlistedKey());
    }
  }

  // A room post goes only to a room its author is in, and stays in that room.
  private void requireRoom(PostEntity post, PostAudience audience, Long roomId) {
    if (audience.inRoom() != (roomId != null)) {
      throw new PostException(
          audience.inRoom()
              ? PostErrorCode.POST_ROOM_REQUIRED
              : PostErrorCode.POST_ROOM_NOT_ALLOWED);
    }
    if (roomId == null) {
      return;
    }
    if (post.getRoomId() != null && !post.getRoomId().equals(roomId)) {
      throw new PostException(PostErrorCode.POST_ROOM_MOVE, post.getRoomId())
          .with("roomId", post.getRoomId());
    }
    requireMember(roomId, post.getUserId());
  }

  public void requireMember(Long roomId, Long userId) {
    if (!rooms.isMember(roomId, userId)) {
      throw new PostException(PostErrorCode.POST_ROOM_NOT_FOUND, roomId);
    }
  }

  public void requireStillInRoom(PostEntity post) {
    if (post.isInRoom()) {
      requireMember(post.getRoomId(), post.getUserId());
    }
  }

  public void rotateUnlistedKey(PostEntity post) {
    post.rotateUnlistedKey(newUnlistedKey());
  }

  private static PostAudience audience(String requested, PostAudience current) {
    if (requested == null) return current;
    return PostAudience.parse(requested)
        .orElseThrow(
            () ->
                new PostException(PostErrorCode.POST_AUDIENCE_INVALID, requested)
                    .with("audience", requested));
  }

  private static List<String> countries(List<String> requested) {
    List<String> invalid =
        requested.stream().filter(code -> !CountryBlocklist.isCountryCode(code)).toList();
    if (!invalid.isEmpty() || requested.stream().distinct().count() > MAX_BLOCKED_COUNTRIES) {
      throw new PostException(PostErrorCode.POST_BLOCKED_COUNTRIES_INVALID, invalid)
          .with("invalid", invalid);
    }
    return requested.stream().map(code -> code.trim().toUpperCase(Locale.ROOT)).toList();
  }

  private static String newUnlistedKey() {
    byte[] key = new byte[24];
    RANDOM.nextBytes(key);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(key);
  }
}
