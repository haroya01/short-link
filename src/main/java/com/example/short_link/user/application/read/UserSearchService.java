package com.example.short_link.user.application.read;

import com.example.short_link.user.domain.repository.UserSearchReader;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserSearchService {

  static final int MIN_QUERY = 2;
  static final int MAX_QUERY = 30;
  static final int MAX_SIZE = 20;
  static final int BIO_LENGTH = 80;

  private final UserSearchReader reader;
  private final Clock clock;

  @Transactional(readOnly = true)
  public UserSearchView search(Long viewerId, String query, int page, int size) {
    int from = Math.max(page, 0);
    int capped = Math.min(Math.max(size, 1), MAX_SIZE);
    String normalized = normalize(query);
    if (normalized.codePointCount(0, normalized.length()) < MIN_QUERY) {
      return new UserSearchView(List.of(), from, capped, false);
    }
    int offset = (int) Math.min((long) from * capped, Integer.MAX_VALUE);
    List<UserSearchReader.Match> rows =
        reader.search(viewerId, normalized, clock.instant(), offset, capped + 1);
    List<UserSearchView.Item> items =
        rows.stream().limit(capped).map(UserSearchService::item).toList();
    return new UserSearchView(items, from, capped, rows.size() > capped);
  }

  static String normalize(String query) {
    if (query == null) {
      return "";
    }
    String trimmed = query.strip();
    if (trimmed.startsWith("@")) {
      trimmed = trimmed.substring(1).strip();
    }
    if (trimmed.codePointCount(0, trimmed.length()) > MAX_QUERY) {
      trimmed = trimmed.substring(0, trimmed.offsetByCodePoints(0, MAX_QUERY));
    }
    return trimmed;
  }

  static String shortBio(String bio) {
    if (bio == null || bio.isBlank()) {
      return null;
    }
    String flat = bio.strip().replaceAll("\\s+", " ");
    return flat.codePointCount(0, flat.length()) <= BIO_LENGTH
        ? flat
        : flat.substring(0, flat.offsetByCodePoints(0, BIO_LENGTH)) + "…";
  }

  private static UserSearchView.Item item(UserSearchReader.Match match) {
    return new UserSearchView.Item(
        match.userId(),
        match.username(),
        match.displayName(),
        match.avatarUrl(),
        shortBio(match.bio()),
        match.followerCountHidden() ? null : match.followerCount(),
        match.following(),
        match.requested());
  }
}
