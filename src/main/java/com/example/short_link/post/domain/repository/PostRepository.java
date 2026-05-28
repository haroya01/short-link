package com.example.short_link.post.domain.repository;

import com.example.short_link.post.domain.PostEntity;
import java.util.List;
import java.util.Optional;

public interface PostRepository {

  Optional<PostEntity> findById(Long id);

  PostEntity save(PostEntity post);

  void delete(PostEntity post);

  boolean existsByUserIdAndSlug(Long userId, String slug);

  List<PostEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId);
}
