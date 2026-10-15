package com.example.short_link.federation.application;

import com.example.short_link.federation.domain.repository.RemoteActorRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RemoteAccountSearch {

  static final int MIN_TERM = 2;
  static final int MAX_LIMIT = 20;

  private final RemoteActorRepository actors;

  @Transactional(readOnly = true)
  public List<RemoteAccountView> search(Long viewerId, String query, int limit) {
    String term = query == null ? "" : query.strip();
    if (term.startsWith("@")) {
      term = term.substring(1);
    }
    if (term.codePointCount(0, term.length()) < MIN_TERM) {
      return List.of();
    }
    return actors.search(viewerId, term, Math.clamp(limit, 1, MAX_LIMIT)).stream()
        .map(RemoteAccountView::of)
        .toList();
  }
}
