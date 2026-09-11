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

/** User and link investigation, account actions, and operational maintenance. */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminOperationsController {

  private final AdminBrowseService browseService;
  private final AdminActivityService activityService;
  private final ReDetectWebhookFormatsUseCase reDetectWebhooks;
  private final MintAccessTokenUseCase mintAccessToken;
  private final WarnUserUseCase warnUser;

  /**
   * Mint a fresh access token for the calling admin — a convenience for scripting against the API
   * (seeding, one-off automation). The token carries the admin's own role only.
   */
  @PostMapping("/access-token")
  public MintedAccessToken mintAccessToken(@AuthenticationPrincipal Long userId) {
    return mintAccessToken.mintFor(userId);
  }

  /**
   * Full user-table browse. {@code q} matches email / handle case-insensitively; {@code role}
   * filters by USER / ADMIN; newest first. Page size is capped server-side.
   */
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

  /**
   * 약관 위반 계정에 보내는 운영자 경고. 링크 알림 인박스에 남고 푸시 설정과 무관하게 푸시된다. {@code shortCode} 는 어느 링크 건인지 가리키는
   * 맥락(선택).
   */
  @PostMapping("/users/{id}/warning")
  public ResponseEntity<Void> warnUser(
      @PathVariable long id, @Valid @RequestBody WarnUserRequest request) {
    warnUser.execute(id, request.shortCode(), request.message());
    return ResponseEntity.noContent().build();
  }

  /**
   * Full link-table browse. {@code q} matches an exact short code or a substring of the destination
   * URL; {@code ownerId} narrows to one user's links (anonymous links have no owner); {@code sort}
   * is {@code recent} (default, newest first) or {@code clicks} (busiest first).
   */
  @GetMapping("/links")
  public AdminBrowseService.LinksPage links(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) Long ownerId,
      @RequestParam(required = false) String sort,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return browseService.links(q, ownerId, sort, page, size);
  }

  /**
   * Live activity feed for the console: newest links and clicks across all users plus the links
   * trending in the last 24h. Cheap enough to poll; click rows are PII-minimal (country / referrer
   * host / device class only — never IP or visitor hash). Declared before {@code /links/{code}} so
   * the literal path wins the match.
   */
  @GetMapping("/links/activity")
  public AdminActivity linkActivity() {
    return activityService.activity();
  }

  /**
   * One link's full metadata (owner, lifecycle, protection) plus the owner-grade click report —
   * daily / hourly / referrer / device / country breakdowns — for support and observability. Reuses
   * the owner stats assembler with the ownership check skipped.
   */
  @GetMapping("/links/{code}")
  public AdminLinkDetail linkDetail(@PathVariable ShortCode code) {
    return browseService.linkDetail(code);
  }

  /**
   * Re-detects {@code WebhookFormat} for every persisted hook and reactivates rows that were
   * auto-disabled by a payload-shape mismatch we just fixed (e.g. a new receiver format added in
   * code, or a URL pattern V53/V54 didn't anticipate). Returns the per-bucket count so the caller
   * can confirm what actually changed before the next click trigger fires.
   */
  @PostMapping("/webhooks/redetect-formats")
  public WebhookReDetectResult redetectWebhookFormats() {
    return reDetectWebhooks.execute();
  }
}
