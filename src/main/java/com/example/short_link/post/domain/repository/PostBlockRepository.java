package com.example.short_link.post.domain.repository;

import com.example.short_link.post.domain.PostBlockEntity;
import java.util.List;

public interface PostBlockRepository {

  /**
   * Uses one multi-row INSERT because Hibernate cannot batch IDENTITY inserts. Generated IDs are
   * not returned; re-read with {@link #findAllByPostIdOrderByBlockOrderAsc} if needed.
   */
  void insertAll(List<PostBlockEntity> blocks);

  List<PostBlockEntity> findAllByPostIdOrderByBlockOrderAsc(Long postId);

  void deleteAllByPostId(Long postId);
}
