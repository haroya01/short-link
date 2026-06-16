package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.post.domain.PostHighlightReplyEntity;
import com.example.short_link.post.domain.repository.PostHighlightReplyRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class PostHighlightReplyRepositoryAdapter implements PostHighlightReplyRepository {

  private final JpaPostHighlightReplyRepository jpa;

  @Override
  public PostHighlightReplyEntity save(PostHighlightReplyEntity reply) {
    return jpa.save(reply);
  }

  @Override
  public Optional<PostHighlightReplyEntity> findById(Long id) {
    return jpa.findById(id);
  }

  @Override
  public void delete(PostHighlightReplyEntity reply) {
    jpa.delete(reply);
  }

  @Override
  public List<PostHighlightReplyEntity> findAllByHighlightIdOrderByCreatedAtAsc(Long highlightId) {
    return jpa.findAllByHighlightIdOrderByCreatedAtAsc(highlightId);
  }

  @Override
  public int deleteAllByHighlightId(Long highlightId) {
    return jpa.deleteAllByHighlightId(highlightId);
  }

  @Override
  public Map<Long, Long> countByHighlightIds(Collection<Long> highlightIds) {
    if (highlightIds.isEmpty()) return Map.of();
    return jpa.countGroupedByHighlightId(highlightIds).stream()
        .collect(
            Collectors.toMap(
                JpaPostHighlightReplyRepository.HighlightReplyCount::getHighlightId,
                JpaPostHighlightReplyRepository.HighlightReplyCount::getCnt));
  }
}
