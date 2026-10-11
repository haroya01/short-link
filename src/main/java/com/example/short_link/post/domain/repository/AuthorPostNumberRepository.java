package com.example.short_link.post.domain.repository;

public interface AuthorPostNumberRepository {

  long next(Long userId);
}
