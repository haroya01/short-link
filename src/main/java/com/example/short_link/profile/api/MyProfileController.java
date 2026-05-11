package com.example.short_link.profile.api;

import com.example.short_link.profile.application.MyProfile;
import com.example.short_link.profile.application.ProfileService;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/profile")
@RequiredArgsConstructor
public class MyProfileController {

  private final ProfileService service;

  @GetMapping
  public MyProfile get(@AuthenticationPrincipal Long userId) {
    return service.myProfile(userId);
  }

  @PutMapping
  public MyProfile update(
      @AuthenticationPrincipal Long userId, @RequestBody UpdateRequest request) {
    return service.updateProfile(
        userId, request.username(), request.bio(), request.theme(), request.shareChannels());
  }

  @PutMapping("/order")
  public MyProfile reorder(
      @AuthenticationPrincipal Long userId, @RequestBody ReorderRequest request) {
    service.reorderProfile(userId, request.items());
    return service.myProfile(userId);
  }

  public record UpdateRequest(
      @Size(max = 32) String username,
      @Size(max = 280) String bio,
      @Pattern(regexp = "^(light|dark|accent|sunset|ocean|forest|mono|neon|aurora)?$") String theme,
      @Size(max = 64) String shareChannels) {}

  public record ReorderRequest(List<ProfileService.ReorderItem> items) {}
}
