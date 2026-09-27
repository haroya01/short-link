package com.example.short_link.post.domain.repository;

public interface PostSearchTextRepository {

  void upsert(Long postId, String searchText);
}
