package com.example.short_link.user.api;

import com.example.short_link.user.application.UserDataExport;
import com.example.short_link.user.application.UserDataExportService;
import com.example.short_link.user.application.UserDeletionService;
import com.example.short_link.user.application.UserNotFoundException;
import com.example.short_link.user.application.UserPreferencesService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserRepository;
import jakarta.validation.Valid;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

  private final UserRepository userRepository;
  private final UserPreferencesService preferencesService;
  private final UserDeletionService deletionService;
  private final UserDataExportService exportService;

  @GetMapping("/me")
  public MeResponse me(@AuthenticationPrincipal Long userId) {
    return userRepository
        .findById(userId)
        .filter(u -> !u.isDeleted())
        .map(
            u ->
                new MeResponse(
                    u.getId(),
                    u.getEmail(),
                    u.getOauthProvider(),
                    u.getRole().name(),
                    u.getTimezone(),
                    u.getCreatedAt(),
                    u.getTier().name(),
                    u.getSubscriptionCurrentPeriodEnd()))
        .orElseThrow(UserNotFoundException::new);
  }

  @PutMapping("/me/preferences")
  public MeResponse updatePreferences(
      @AuthenticationPrincipal Long userId, @Valid @RequestBody UpdatePreferencesRequest request) {
    UserEntity user = preferencesService.updateTimezone(userId, request.timezone());
    return new MeResponse(
        user.getId(),
        user.getEmail(),
        user.getOauthProvider(),
        user.getRole().name(),
        user.getTimezone(),
        user.getCreatedAt(),
        user.getTier().name(),
        user.getSubscriptionCurrentPeriodEnd());
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

  @DeleteMapping("/me")
  public ResponseEntity<Void> deleteMe(@AuthenticationPrincipal Long userId) {
    deletionService.deleteAccount(userId);
    return ResponseEntity.noContent().build();
  }
}
