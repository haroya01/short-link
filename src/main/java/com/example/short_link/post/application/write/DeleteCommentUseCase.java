package com.example.short_link.post.application.write;

import com.example.short_link.post.domain.CommentEntity;
import com.example.short_link.post.domain.repository.CommentRepository;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeleteCommentUseCase {

  private final CommentRepository commentRepository;
  private final PostRepository postRepository;

  @Transactional
  public void execute(DeleteCommentCommand cmd) {
    CommentEntity comment =
        commentRepository
            .findById(cmd.commentId())
            .orElseThrow(() -> new PostException(PostErrorCode.COMMENT_NOT_FOUND, cmd.commentId()));

    if (!comment.isOwnedBy(cmd.userId()) && !isPostOwner(comment.getPostId(), cmd.userId())) {
      throw new PostException(PostErrorCode.COMMENT_PERMISSION_DENIED)
          .with("commentId", cmd.commentId());
    }

    if (!comment.isReply()) {
      List<CommentEntity> replies = commentRepository.findAllByParentId(comment.getId());
      if (replies.stream().anyMatch(reply -> !reply.isDeleted())) {
        comment.tombstone();
        commentRepository.save(comment);
        return;
      }
      replies.forEach(commentRepository::delete);
    }
    commentRepository.delete(comment);
  }

  private boolean isPostOwner(Long postId, Long userId) {
    return postRepository.findById(postId).map(p -> p.isOwnedBy(userId)).orElse(false);
  }
}
