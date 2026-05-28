package com.example.short_link.post.domain.repository;

import com.example.short_link.post.domain.PostEntity;

public interface PostRepository {

  PostEntity save(PostEntity post);

  boolean existsByUserIdAndSlug(Long userId, String slug);
}
