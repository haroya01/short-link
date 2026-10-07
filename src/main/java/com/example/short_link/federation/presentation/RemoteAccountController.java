package com.example.short_link.federation.presentation;

import com.example.short_link.federation.application.RemoteAccountView;
import com.example.short_link.federation.application.RemoteFollowing;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/federation")
@RequiredArgsConstructor
public class RemoteAccountController {

  private static final int MAX_PAGE_SIZE = 50;

  private final RemoteFollowing following;

  @GetMapping("/accounts/lookup")
  public RemoteAccountView lookup(
      @AuthenticationPrincipal Long userId, @RequestParam("acct") String acct) {
    return following.lookup(userId, acct);
  }

  @GetMapping("/accounts/{id}")
  public RemoteAccountView account(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    return following.account(userId, id);
  }

  @PostMapping("/accounts/{id}/follow")
  public RemoteAccountView follow(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    return following.follow(userId, id);
  }

  @DeleteMapping("/accounts/{id}/follow")
  public RemoteAccountView unfollow(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    return following.unfollow(userId, id);
  }

  @GetMapping("/following")
  public List<RemoteAccountView> followingList(
      @AuthenticationPrincipal Long userId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return following.following(
        userId, Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
  }
}
