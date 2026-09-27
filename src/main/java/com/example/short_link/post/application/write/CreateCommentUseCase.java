package com.example.short_link.post.application.write;

import com.example.short_link.post.application.read.CommentView;
import com.example.short_link.post.application.read.PublicAuthorView;
import com.example.short_link.post.domain.CommentEntity;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.CommentRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateCommentUseCase {

  private final PostInteractionAccess access;
  private final CommentRepository commentRepository;
  private final UserRepository userRepository;
  private final CommentNotifications notifications;

  @Transactional
  public CommentView execute(CreateCommentCommand cmd) {
    PostEntity post = access.requireCommentablePost(cmd.userId(), cmd.postId());

    CommentEntity parent = null;
    if (cmd.parentId() != null) {
      parent =
          commentRepository
              .findById(cmd.parentId())
              .orElseThrow(() -> new PostException(PostErrorCode.COMMENT_PARENT_INVALID));
      if (!parent.acceptsReplyOn(cmd.postId())) {
        throw new PostException(PostErrorCode.COMMENT_PARENT_INVALID);
      }
    }

    CommentEntity saved =
        commentRepository.save(
            new CommentEntity(cmd.postId(), cmd.userId(), cmd.parentId(), cmd.body().trim()));

    notifications.commentCreated(post, parent, saved, cmd.body());

    UserEntity author = userRepository.findById(cmd.userId()).orElse(null);
    return new CommentView(
        saved.getId(),
        saved.getParentId(),
        author == null ? null : PublicAuthorView.from(author),
        saved.getBody(),
        saved.getCreatedAt(),
        0L);
  }
}
