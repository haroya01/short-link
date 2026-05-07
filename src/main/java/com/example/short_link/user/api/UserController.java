package com.example.short_link.user.api;

import com.example.short_link.user.application.UserNotFoundException;
import com.example.short_link.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

  private final UserRepository userRepository;

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
                    u.getCreatedAt()))
        .orElseThrow(UserNotFoundException::new);
  }
}
