package com.example.short_link.post.presentation;

import com.example.short_link.post.application.read.FeedPrefQueryService;
import com.example.short_link.post.application.read.FeedPrefsView;
import com.example.short_link.post.application.write.SetFeedPrefUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authenticated per-user feed preferences — the blog-home default tab. PUT returns the full updated
 * prefs so the caller replaces its state in one round-trip (mirrors the tag-pref controller).
 */
@RestController
@RequestMapping("/api/v1/users/me/feed-prefs")
@RequiredArgsConstructor
public class UserFeedPrefController {

  private final SetFeedPrefUseCase setFeedPref;
  private final FeedPrefQueryService feedPrefQueryService;

  @GetMapping
  public FeedPrefsView get(@AuthenticationPrincipal Long userId) {
    return feedPrefQueryService.get(userId);
  }

  @PutMapping("/default-tab/{tab}")
  public FeedPrefsView setDefaultTab(
      @AuthenticationPrincipal Long userId, @PathVariable String tab) {
    setFeedPref.setDefaultTab(userId, tab);
    return feedPrefQueryService.get(userId);
  }
}
