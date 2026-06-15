package com.example.short_link.post.presentation;

import com.example.short_link.post.application.read.ReadingHistoryQueryService;
import com.example.short_link.post.application.read.ReadingHistoryView;
import com.example.short_link.post.application.write.RecordPostReadUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The reader's private reading history. All authenticated (it's per-user): a read beacon on a post,
 * the paged history list, and clear-all / forget-one for control over what's kept.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReadingHistoryController {

  private static final int MAX_SIZE = 50;

  private final RecordPostReadUseCase recordPostRead;
  private final ReadingHistoryQueryService readingHistoryQueryService;

  @PostMapping("/posts/{postId}/read")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void record(@AuthenticationPrincipal Long userId, @PathVariable Long postId) {
    recordPostRead.record(userId, postId);
  }

  @GetMapping("/users/me/reading-history")
  public ReadingHistoryView history(
      @AuthenticationPrincipal Long userId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return readingHistoryQueryService.list(
        userId, Math.max(page, 0), Math.min(Math.max(size, 1), MAX_SIZE));
  }

  @DeleteMapping("/users/me/reading-history")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void clear(@AuthenticationPrincipal Long userId) {
    recordPostRead.clear(userId);
  }

  @DeleteMapping("/users/me/reading-history/{postId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void forget(@AuthenticationPrincipal Long userId, @PathVariable Long postId) {
    recordPostRead.remove(userId, postId);
  }
}
