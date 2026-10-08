package com.example.short_link.user.application.read;

import com.example.short_link.user.domain.repository.MentionCandidateReader;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MentionCandidateService {

  static final int MAX_LIMIT = 10;
  static final int MAX_QUERY = 30;

  private final MentionCandidateReader reader;

  @Transactional(readOnly = true)
  public List<MentionCandidateReader.Candidate> candidates(Long userId, String query, int limit) {
    int capped = Math.min(Math.max(limit, 1), MAX_LIMIT);
    String prefix = normalize(query);
    return prefix.isEmpty()
        ? reader.followed(userId, capped)
        : reader.matching(userId, prefix, capped);
  }

  static String normalize(String query) {
    if (query == null) {
      return "";
    }
    String trimmed = query.strip();
    if (trimmed.startsWith("@")) {
      trimmed = trimmed.substring(1);
    }
    if (trimmed.length() > MAX_QUERY) {
      trimmed = trimmed.substring(0, MAX_QUERY);
    }
    return trimmed.toLowerCase(Locale.ROOT);
  }
}
