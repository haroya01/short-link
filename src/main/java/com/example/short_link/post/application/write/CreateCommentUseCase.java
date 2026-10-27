package com.example.short_link.post.application.write;

import com.example.short_link.post.application.access.PostReader;
import com.example.short_link.post.application.read.CommentMentions;
import com.example.short_link.post.application.read.CommentView;
import com.example.short_link.post.application.read.PublicAuthorView;
import com.example.short_link.post.domain.CommentEntity;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.CommentRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import com.example.short_link.user.application.read.UserSummary;
import com.example.short_link.user.application.read.UserSummaryReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateCommentUseCase {

  private final PostInteractionAccess access;
  private final CommentRepository commentRepository;
  private final UserSummaryReader users;
  private final CommentNotifications notifications;
  private final CommentMentions mentions;

  @Transactional
  public CommentView execute(CreateCommentCommand cmd, PostReader reader) {
    PostEntity post = access.requireCommentablePost(reader, cmd.postId());

    CommentEntity parent = null;
    if (cmd.parentId() != null) {
      parent =
          commentRepository
              .findById(cmd.parentId())
              .orElseThrow(() -> new PostException(PostErrorCode.COMMENT_PARENT_INVALID));
      if (!parent.acceptsReplyOn(cmd.postId()) || !access.hears(parent, reader)) {
        throw new PostException(PostErrorCode.COMMENT_PARENT_INVALID);
      }
    }

    CommentEntity comment =
        new CommentEntity(cmd.postId(), cmd.userId(), cmd.parentId(), cmd.body().trim());
    comment.saidIn(
        parent != null && parent.getRoomId() != null ? parent.getRoomId() : post.getRoomId());
    CommentEntity saved = commentRepository.save(comment);

    notifications.commentCreated(post, parent, saved, cmd.body());

    UserSummary author = users.find(cmd.userId()).orElse(null);
    return new CommentView(
        saved.getId(),
        saved.getParentId(),
        author == null ? null : PublicAuthorView.from(author),
        saved.getBody(),
        saved.getCreatedAt(),
        0L,
        mentions.of(saved.getBody()));
  }
}
