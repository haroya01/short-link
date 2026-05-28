package com.example.short_link.post.domain.repository;

import com.example.short_link.post.domain.PostBlockEntity;
import java.util.List;

public interface PostBlockRepository {

  List<PostBlockEntity> saveAll(List<PostBlockEntity> blocks);

  List<PostBlockEntity> findAllByPostIdOrderByBlockOrderAsc(Long postId);

  void deleteAllByPostId(Long postId);
}
