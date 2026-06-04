package com.example.short_link.post.webhook.presentation;

import com.example.short_link.post.webhook.application.dto.BlogWebhookSummary;
import com.example.short_link.post.webhook.application.dto.IssuedBlogWebhook;
import com.example.short_link.post.webhook.application.read.BlogWebhookQueryService;
import com.example.short_link.post.webhook.application.write.DeleteBlogWebhookUseCase;
import com.example.short_link.post.webhook.application.write.RegisterBlogWebhookUseCase;
import com.example.short_link.post.webhook.application.write.UpdateBlogWebhookUseCase;
import com.example.short_link.post.webhook.presentation.request.BlogWebhookRegisterRequest;
import com.example.short_link.post.webhook.presentation.request.BlogWebhookUpdateRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Author-private CRUD for blog notification webhooks — fire on likes/comments/follows/series
 * subscriptions across all of the signed-in author's posts. Scoped to the user, not a single post.
 */
@RestController
@RequestMapping("/api/v1/blog/webhooks")
@RequiredArgsConstructor
public class BlogWebhookController {

  private final BlogWebhookQueryService queryService;
  private final RegisterBlogWebhookUseCase registerUseCase;
  private final UpdateBlogWebhookUseCase updateUseCase;
  private final DeleteBlogWebhookUseCase deleteUseCase;

  @GetMapping
  public List<BlogWebhookSummary> list(@AuthenticationPrincipal Long userId) {
    return queryService.listForUser(userId);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public IssuedBlogWebhook register(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody BlogWebhookRegisterRequest req) {
    return registerUseCase.execute(userId, req.url(), req.name(), req.events());
  }

  @PatchMapping("/{id}")
  public BlogWebhookSummary update(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long id,
      @Valid @RequestBody BlogWebhookUpdateRequest req) {
    return updateUseCase.execute(userId, id, req.name(), req.events(), req.enabled());
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    deleteUseCase.execute(userId, id);
  }
}
