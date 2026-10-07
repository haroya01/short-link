package com.example.short_link.user.application.read;

import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.FollowSuggestionReader;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserErrorCode;
import com.example.short_link.user.exception.UserException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Mastodon's follow suggestions: who the people a member follows follow, then accounts much
// followed and active in the last thirty days. Setting one aside keeps it out for good.
@Service
public class FollowSuggestionService {

  static final int MAX_LIMIT = 40;
  static final Duration ACTIVE = Duration.ofDays(30);

  private final FollowSuggestionReader reader;
  private final UserRepository users;
  private final Clock clock;

  @Autowired
  public FollowSuggestionService(FollowSuggestionReader reader, UserRepository users) {
    this(reader, users, Clock.systemUTC());
  }

  FollowSuggestionService(FollowSuggestionReader reader, UserRepository users, Clock clock) {
    this.reader = reader;
    this.users = users;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<FollowSuggestionReader.Suggestion> suggestions(Long userId, int limit) {
    return reader.suggestions(
        userId, clock.instant().minus(ACTIVE), Math.min(Math.max(limit, 1), MAX_LIMIT));
  }

  @Transactional
  public void dismiss(Long userId, String username) {
    UserEntity dismissed =
        users
            .findByUsername(username)
            .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
    reader.dismiss(userId, dismissed.getId());
  }
}
