package com.example.short_link.post.presentation;

import com.example.short_link.post.application.read.CommentLikeStatus;
import com.example.short_link.post.application.read.CommentView;
import com.example.short_link.post.application.read.PostCommentQueryService;
import com.example.short_link.post.application.write.CreateCommentCommand;
import com.example.short_link.post.application.write.CreateCommentUseCase;
import com.example.short_link.post.application.write.DeleteCommentCommand;
import com.example.short_link.post.application.write.DeleteCommentUseCase;
import com.example.short_link.post.application.write.LikeCommentUseCase;
import com.example.short_link.post.presentation.request.CreateCommentRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CommentController {

  private final CreateCommentUseCase createComment;
  private final DeleteCommentUseCase deleteComment;
  private final LikeCommentUseCase likeComment;
  private final PostCommentQueryService commentQuery;

  @PostMapping("/posts/{postId}/comments")
  @ResponseStatus(HttpStatus.CREATED)
  public CommentView create(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long postId,
      @Valid @RequestBody CreateCommentRequest request) {
    return createComment.execute(
        new CreateCommentCommand(userId, postId, request.parentId(), request.body()));
  }

  @DeleteMapping("/comments/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    deleteComment.execute(new DeleteCommentCommand(userId, id));
  }

  @PostMapping("/comments/{id}/like")
  public CommentLikeStatus like(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    return likeComment.like(userId, id);
  }

  @DeleteMapping("/comments/{id}/like")
  public CommentLikeStatus unlike(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    return likeComment.unlike(userId, id);
  }

  /** 공개 댓글 목록은 비인증 — 보는 사람의 likedByMe 만 이 인증 엔드포인트가 따로 답한다. */
  @GetMapping("/posts/{postId}/comments/liked")
  public List<Long> likedCommentIds(
      @AuthenticationPrincipal Long userId, @PathVariable Long postId) {
    return commentQuery.likedCommentIds(userId, postId);
  }
}
