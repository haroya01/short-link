package com.example.short_link.profile.api;

import com.example.short_link.profile.api.response.PublicProfileHandleItem;
import com.example.short_link.profile.api.response.PublicProfileListResponse;
import com.example.short_link.profile.application.PublicProfile;
import com.example.short_link.profile.application.read.ProfileQueryService;
import java.util.List;
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

  /**
   * Paginated listing of public profile handles. Anonymous endpoint used by the frontend sitemap
   * generator to enumerate /u/&lt;handle&gt; pages for Google to index. Returns just the handle —
   * the per-profile content is fetched separately via the /{username} endpoint when each page is
   * actually crawled, so this stays cheap at any user count.
   */
  @GetMapping
  public PublicProfileListResponse list(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "500") int size) {
    int safeSize = Math.min(Math.max(size, 1), LIST_MAX_PAGE_SIZE);
    int safePage = Math.max(page, 0);
    ProfileQueryService.PublicHandlesPage p = queryService.publicHandlesPage(safePage, safeSize);
    List<PublicProfileHandleItem> items =
        p.handles().stream().map(PublicProfileHandleItem::new).toList();
    return new PublicProfileListResponse(items, p.total());
  }
}
