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

  List<PostHighlightReplyEntity> findAllByHighlightIdOrderByCreatedAtAsc(Long highlightId);

  int deleteAllByHighlightId(Long highlightId);

  Map<Long, Long> countByHighlightIds(Collection<Long> highlightIds);
}
