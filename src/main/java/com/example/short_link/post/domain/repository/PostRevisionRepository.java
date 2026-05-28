package com.example.short_link.post.domain.repository;

import com.example.short_link.post.domain.PostRevisionEntity;
import java.util.List;
import java.util.Optional;

public interface PostRevisionRepository {

  PostRevisionEntity save(PostRevisionEntity revision);

  Optional<PostRevisionEntity> findLatestByPostId(Long postId);

  List<PostRevisionEntity> findAllByPostIdOrderByVersionNumberDesc(Long postId);

  void deleteAllByPostId(Long postId);
}
