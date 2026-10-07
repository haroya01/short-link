package com.example.short_link.user.presentation;

import com.example.short_link.user.application.dto.UserDataExport;
import com.example.short_link.user.application.read.AccountExportService;
import com.example.short_link.user.application.read.UserDataExportService;
import com.example.short_link.user.application.read.UserQueryService;
import com.example.short_link.user.application.write.UserDeletionService;
import com.example.short_link.user.application.write.UserPreferencesService;
import com.example.short_link.user.presentation.request.UpdatePreferencesRequest;
import com.example.short_link.user.presentation.response.MeResponse;
import jakarta.validation.Valid;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

  private final UserQueryService queryService;
  private final UserPreferencesService preferencesService;
  private final UserDeletionService deletionService;
  private final UserDataExportService exportService;
  private final AccountExportService accountExports;

  @GetMapping("/me")
  public MeResponse me(@AuthenticationPrincipal Long userId) {
    return MeResponse.from(queryService.activeOrThrow(userId));
  }

  @PutMapping("/me/preferences")
  public MeResponse updatePreferences(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody UpdatePreferencesRequest request) {
    return MeResponse.from(preferencesService.updateTimezone(userId, request.timezone()));
  }

  @GetMapping("/me/export")
  public ResponseEntity<UserDataExport> exportData(@AuthenticationPrincipal Long userId) {
    UserDataExport data = exportService.export(userId);
    String filename = "kurl-export-" + userId + "-" + Instant.now().getEpochSecond() + ".json";
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
        .body(data);
  }

  // Mastodon's CSV exports, one file per kind (following, blocks, mutes, domain-blocks,
  // bookmarks, lists).
  @GetMapping("/me/exports/{kind}")
  public ResponseEntity<String> exportCsv(
      @AuthenticationPrincipal Long userId, @PathVariable String kind) {
    AccountExportService.Kind which = AccountExportService.Kind.of(kind);
    return ResponseEntity.ok()
        .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
        .header(
            HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + which.filename() + "\"")
        .body(accountExports.csv(userId, which));
  }

  @DeleteMapping("/me")
  public ResponseEntity<Void> deleteMe(@AuthenticationPrincipal Long userId) {
    deletionService.deleteAccount(userId);
    return ResponseEntity.noContent().build();
  }
}
