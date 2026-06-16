package com.example.short_link.post.domain.repository;

import com.example.short_link.post.domain.PostHighlightReplyEntity;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface PostHighlightReplyRepository {

  PostHighlightReplyEntity save(PostHighlightReplyEntity reply);

  Optional<PostHighlightReplyEntity> findById(Long id);

  void delete(PostHighlightReplyEntity reply);

  /** A highlight's replies, oldest first — the flat thread render order. */
  List<PostHighlightReplyEntity> findAllByHighlightIdOrderByCreatedAtAsc(Long highlightId);

  /** Purge a highlight's replies — used when the highlight is removed without DB cascade. */
  int deleteAllByHighlightId(Long highlightId);

  /** Reply counts for a batch of highlights — one GROUP BY for the whole highlight list render. */
  Map<Long, Long> countByHighlightIds(Collection<Long> highlightIds);
}
