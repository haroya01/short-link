package com.example.short_link.link.stats.presentation;

import com.example.short_link.link.application.dto.AdminLinkClicksResult;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.presentation.request.ClickSelectionRequest;
import com.example.short_link.link.presentation.response.AdminLinkClickResponse;
import com.example.short_link.link.presentation.response.AdminLinkClicksPage;
import com.example.short_link.link.presentation.response.ClickExclusionResponse;
import com.example.short_link.link.stats.application.ClickExclusionService;
import com.example.short_link.link.stats.application.LinkEventsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/links/{code}/clicks")
@RequiredArgsConstructor
public class AdminLinkClicksController {

  private final LinkEventsService events;
  private final ClickExclusionService exclusions;

  @GetMapping
  public AdminLinkClicksPage clicks(
      @PathVariable ShortCode code,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit) {
    AdminLinkClicksResult result = events.clicksForAdmin(code, cursor, limit);
    return new AdminLinkClicksPage(
        result.items().stream().map(AdminLinkClickResponse::from).toList(), result.nextCursor());
  }

  @PostMapping("/exclude")
  public ClickExclusionResponse exclude(
      @AuthenticationPrincipal Long adminUserId,
      @PathVariable ShortCode code,
      @Valid @RequestBody ClickSelectionRequest request) {
    return new ClickExclusionResponse(
        code.value(), exclusions.exclude(adminUserId, code, request.clickIds()));
  }

  @PostMapping("/restore")
  public ClickExclusionResponse restore(
      @AuthenticationPrincipal Long adminUserId,
      @PathVariable ShortCode code,
      @Valid @RequestBody ClickSelectionRequest request) {
    return new ClickExclusionResponse(
        code.value(), exclusions.restore(adminUserId, code, request.clickIds()));
  }
}
