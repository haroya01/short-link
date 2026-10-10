package com.example.short_link.user.presentation;

import com.example.short_link.user.application.read.UserSearchService;
import com.example.short_link.user.application.read.UserSearchView;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserSearchController {

  private final UserSearchService userSearchService;

  @GetMapping("/api/v1/public/users/search")
  public UserSearchView search(
      @AuthenticationPrincipal Long viewerId,
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return userSearchService.search(viewerId, q, page, size);
  }
}
