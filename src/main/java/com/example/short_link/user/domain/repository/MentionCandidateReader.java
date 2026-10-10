package com.example.short_link.user.domain.repository;

import java.util.List;

public interface MentionCandidateReader {

  record Candidate(
      Long userId, String username, String displayName, String avatarUrl, boolean following) {}

  List<Candidate> followed(Long userId, int limit);

  List<Candidate> matching(Long userId, String prefix, int limit);
}
