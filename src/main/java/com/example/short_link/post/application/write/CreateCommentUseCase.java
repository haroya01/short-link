package com.example.short_link.post.application.write;

import com.example.short_link.post.application.read.CommentView;
import com.example.short_link.post.application.read.PublicAuthorView;
import com.example.short_link.post.domain.CommentEntity;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.CommentRepository;
import com.example.short_link.post.domain.repository.PostRepository;
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

  private final PostRepository postRepository;
  private final CommentRepository commentRepository;
  private final UserRepository userRepository;

  @Transactional
  public CommentView execute(CreateCommentCommand cmd) {
    PostEntity post =
        postRepository
            .findById(cmd.postId())
            .orElseThrow(() -> new PostException(PostErrorCode.POST_NOT_FOUND, cmd.postId()));
    if (!post.isPublished()) {
      throw new PostException(PostErrorCode.POST_NOT_FOUND, cmd.postId());
    }

    if (cmd.parentId() != null) {
      CommentEntity parent =
          commentRepository
              .findById(cmd.parentId())
              .orElseThrow(() -> new PostException(PostErrorCode.COMMENT_PARENT_INVALID));
      // Parent must be a top-level comment on the same post (only one level of threading).
      if (!parent.getPostId().equals(cmd.postId()) || parent.isReply()) {
        throw new PostException(PostErrorCode.COMMENT_PARENT_INVALID);
      }
    }

    CommentEntity saved =
        commentRepository.save(
            new CommentEntity(cmd.postId(), cmd.userId(), cmd.parentId(), cmd.body().trim()));
    UserEntity author = userRepository.findById(cmd.userId()).orElse(null);
    return new CommentView(
        saved.getId(),
        saved.getParentId(),
        author == null ? null : PublicAuthorView.from(author),
        saved.getBody(),
        saved.getCreatedAt());
  }
}
