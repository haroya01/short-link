package com.example.short_link.post.presentation;

import com.example.short_link.post.application.read.HighlightReplyLikeStatus;
import com.example.short_link.post.application.read.HighlightReplyView;
import com.example.short_link.post.application.write.CreateHighlightReplyCommand;
import com.example.short_link.post.application.write.CreateHighlightReplyUseCase;
import com.example.short_link.post.application.write.DeleteHighlightReplyCommand;
import com.example.short_link.post.application.write.DeleteHighlightReplyUseCase;
import com.example.short_link.post.application.write.LikeHighlightReplyUseCase;
import com.example.short_link.post.presentation.request.CreateHighlightReplyRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class HighlightReplyController {

  private final CreateHighlightReplyUseCase createReply;
  private final DeleteHighlightReplyUseCase deleteReply;
  private final LikeHighlightReplyUseCase likeReply;

  @PostMapping("/highlights/{highlightId}/replies")
  @ResponseStatus(HttpStatus.CREATED)
  public HighlightReplyView create(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long highlightId,
      @Valid @RequestBody CreateHighlightReplyRequest request) {
    return createReply.execute(
        new CreateHighlightReplyCommand(userId, highlightId, request.body()));
  }

  @DeleteMapping("/highlight-replies/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    deleteReply.execute(new DeleteHighlightReplyCommand(userId, id));
  }

  @PostMapping("/highlight-replies/{id}/like")
  public HighlightReplyLikeStatus like(
      @AuthenticationPrincipal Long userId, @PathVariable Long id) {
    return likeReply.like(userId, id);
  }

  @DeleteMapping("/highlight-replies/{id}/like")
  public HighlightReplyLikeStatus unlike(
      @AuthenticationPrincipal Long userId, @PathVariable Long id) {
    return likeReply.unlike(userId, id);
  }
}
