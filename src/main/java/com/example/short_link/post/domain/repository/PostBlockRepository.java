package com.example.short_link.post.domain.repository;

import com.example.short_link.post.domain.PostBlockEntity;
import java.util.List;

public interface PostBlockRepository {

  /**
   * Persist all blocks in a single multi-row INSERT. Unlike {@code saveAll} on an IDENTITY entity —
   * which Hibernate cannot batch, so it emits one INSERT per block (a write N+1) — this is one
   * round-trip. The generated ids are not returned; re-read with {@link
   * #findAllByPostIdOrderByBlockOrderAsc} when the caller needs them.
   */
  void insertAll(List<PostBlockEntity> blocks);

  List<PostBlockEntity> findAllByPostIdOrderByBlockOrderAsc(Long postId);

  void deleteAllByPostId(Long postId);
}
