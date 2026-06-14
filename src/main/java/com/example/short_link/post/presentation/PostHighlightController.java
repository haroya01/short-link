package com.example.short_link.post.presentation;

import com.example.short_link.post.application.read.HighlightRef;
import com.example.short_link.post.application.read.MyHighlightView;
import com.example.short_link.post.application.read.PostHighlightQueryService;
import com.example.short_link.post.application.write.CreateHighlightCommand;
import com.example.short_link.post.application.write.CreateHighlightUseCase;
import com.example.short_link.post.presentation.request.CreateHighlightRequest;
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

/** Authed highlight surface — create/delete a highlight, and the reader's own library. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PostHighlightController {

  private final CreateHighlightUseCase createHighlight;
  private final PostHighlightQueryService highlightQuery;

  @PostMapping("/posts/{postId}/highlights")
  @ResponseStatus(HttpStatus.CREATED)
  public HighlightRef create(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long postId,
      @Valid @RequestBody CreateHighlightRequest request) {
    return createHighlight.execute(
        new CreateHighlightCommand(
            userId,
            postId,
            request.blockOrder(),
            request.startOffset(),
            request.endOffset(),
            request.quote()));
  }

  @DeleteMapping("/highlights/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    createHighlight.delete(userId, id);
  }

  @GetMapping("/users/me/highlights")
  public List<MyHighlightView> myHighlights(@AuthenticationPrincipal Long userId) {
    return highlightQuery.listMine(userId);
  }
}
