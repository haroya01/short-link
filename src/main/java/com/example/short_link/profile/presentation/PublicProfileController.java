package com.example.short_link.profile.presentation;

import com.example.short_link.profile.application.PublicProfile;
import com.example.short_link.profile.application.read.ProfileQueryService;
import com.example.short_link.profile.presentation.response.PublicProfileListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/profiles")
@RequiredArgsConstructor
public class PublicProfileController {

  private static final int LIST_MAX_PAGE_SIZE = 1000;

  private final ProfileQueryService queryService;

  @GetMapping("/{username}")
  public PublicProfile publicProfile(@PathVariable String username) {
    return queryService.findByUsername(username);
  }

  @GetMapping
  public PublicProfileListResponse list(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "500") int size) {
    int safeSize = Math.min(Math.max(size, 1), LIST_MAX_PAGE_SIZE);
    int safePage = Math.max(page, 0);
    return PublicProfileListResponse.from(queryService.publicHandlesPage(safePage, safeSize));
  }
}
