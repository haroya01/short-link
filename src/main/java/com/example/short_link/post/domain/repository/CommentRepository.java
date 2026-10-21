package com.example.short_link.post.domain.repository;

import com.example.short_link.common.reply.ReplyPreview;
import com.example.short_link.post.domain.CommentEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CommentRepository {

  // The newest comments of each post, at most perPost each, oldest first, in one statement: only
  // what the viewer hears and, for what was said in a room, only for its members.
  List<ReplyPreview> latestHeard(Collection<Long> postIds, Long viewerId, int perPost);

  CommentEntity save(CommentEntity comment);

  Optional<CommentEntity> findById(Long id);

  void delete(CommentEntity comment);

  List<CommentEntity> findAllByPostIdOrderByCreatedAtAsc(Long postId);

  // A reply stays out with its parent when the viewer cannot hear the parent's writer.
  List<CommentEntity> findHeardByPostId(Long postId, Long viewerId);

  // As findHeardByPostId, plus each deleted top-level comment that still has a reply the viewer
  // hears. Such a parent has no writer to hear, so only the replies' own writers can hide them.
  List<CommentEntity> findHeardWithTombstonesByPostId(Long postId, Long viewerId);

  List<CommentEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId);

  List<CommentEntity> findAllByParentId(Long parentId);

  int deleteAllByPostId(Long postId);
}
