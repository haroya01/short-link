package com.example.short_link.admin.presentation;

import com.example.short_link.admin.application.dto.AdminActivity;
import com.example.short_link.admin.application.dto.AdminLinkDetail;
import com.example.short_link.admin.application.dto.AdminUserRow;
import com.example.short_link.admin.application.read.AdminActivityService;
import com.example.short_link.admin.application.read.AdminBrowseService;
import com.example.short_link.admin.application.write.WarnUserUseCase;
import com.example.short_link.admin.presentation.request.WarnUserRequest;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.webhook.application.dto.WebhookReDetectResult;
import com.example.short_link.link.webhook.application.write.ReDetectWebhookFormatsUseCase;
import com.example.short_link.user.application.dto.MintedAccessToken;
import com.example.short_link.user.application.write.MintAccessTokenUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminOperationsController {

  private final AdminBrowseService browseService;
  private final AdminActivityService activityService;
  private final ReDetectWebhookFormatsUseCase reDetectWebhooks;
  private final MintAccessTokenUseCase mintAccessToken;
  private final WarnUserUseCase warnUser;

  @PostMapping("/access-token")
  public MintedAccessToken mintAccessToken(@AuthenticationPrincipal Long userId) {
    return mintAccessToken.mintFor(userId);
  }

  @GetMapping("/users")
  public AdminBrowseService.UsersPage users(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String role,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return browseService.users(q, role, page, size);
  }

  @GetMapping("/users/{id}")
  public AdminUserRow user(@PathVariable long id) {
    return browseService.user(id);
  }

  @PostMapping("/users/{id}/warning")
  public ResponseEntity<Void> warnUser(
      @PathVariable long id, @Valid @RequestBody WarnUserRequest request) {
    warnUser.execute(id, request.shortCode(), request.message());
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/links")
  public AdminBrowseService.LinksPage links(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) Long ownerId,
      @RequestParam(required = false) String sort,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return browseService.links(q, ownerId, sort, page, size);
  }

  @GetMapping("/links/activity")
  public AdminActivity linkActivity() {
    return activityService.activity();
  }

  @GetMapping("/links/{code}")
  public AdminLinkDetail linkDetail(@PathVariable ShortCode code) {
    return browseService.linkDetail(code);
  }

  /** 포맷을 다시 판별하고 포맷 불일치로 비활성화된 웹훅을 재활성화한다. */
  @PostMapping("/webhooks/redetect-formats")
  public WebhookReDetectResult redetectWebhookFormats() {
    return reDetectWebhooks.execute();
  }
}
