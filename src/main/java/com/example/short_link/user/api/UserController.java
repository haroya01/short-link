package com.example.short_link.user.api;

import com.example.short_link.user.application.UserNotFoundException;
import com.example.short_link.user.application.UserPreferencesService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

  @GetMapping("/me")
  public MeResponse me(@AuthenticationPrincipal Long userId) {
    return userRepository
        .findById(userId)
        .map(
            u ->
                new MeResponse(
                    u.getId(),
                    u.getEmail(),
                    u.getOauthProvider(),
                    u.getRole().name(),
                    u.getTimezone(),
                    u.getCreatedAt()))
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
        user.getCreatedAt());
  }
}
