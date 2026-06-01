package com.example.short_link.post.presentation;

import com.example.short_link.post.application.read.TagPrefQueryService;
import com.example.short_link.post.application.read.TagPrefsView;
import com.example.short_link.post.application.write.SetTagPrefUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authenticated per-user tag preferences (account-synced "follow / hide topics"). Each mutation
 * returns the full updated prefs so the caller can replace its state in one round-trip.
 */
@RestController
@RequestMapping("/api/v1/users/me/tag-prefs")
@RequiredArgsConstructor
public class UserTagPrefController {

  private final SetTagPrefUseCase setTagPref;
  private final TagPrefQueryService tagPrefQueryService;

  @GetMapping
  public TagPrefsView get(@AuthenticationPrincipal Long userId) {
    return tagPrefQueryService.get(userId);
  }

  @PutMapping("/followed/{tag}")
  public TagPrefsView follow(@AuthenticationPrincipal Long userId, @PathVariable String tag) {
    setTagPref.follow(userId, tag);
    return tagPrefQueryService.get(userId);
  }

  @DeleteMapping("/followed/{tag}")
  public TagPrefsView unfollow(@AuthenticationPrincipal Long userId, @PathVariable String tag) {
    setTagPref.unfollow(userId, tag);
    return tagPrefQueryService.get(userId);
  }

  @PutMapping("/hidden/{tag}")
  public TagPrefsView hide(@AuthenticationPrincipal Long userId, @PathVariable String tag) {
    setTagPref.hide(userId, tag);
    return tagPrefQueryService.get(userId);
  }

  @DeleteMapping("/hidden/{tag}")
  public TagPrefsView unhide(@AuthenticationPrincipal Long userId, @PathVariable String tag) {
    setTagPref.unhide(userId, tag);
    return tagPrefQueryService.get(userId);
  }
}
