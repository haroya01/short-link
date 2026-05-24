package com.example.short_link.profile.api;

import com.example.short_link.profile.application.MyProfile;
import com.example.short_link.profile.application.read.ProfileQueryService;
import com.example.short_link.profile.application.write.ReorderItem;
import com.example.short_link.profile.application.write.ReorderProfileCommand;
import com.example.short_link.profile.application.write.ReorderProfileUseCase;
import com.example.short_link.profile.application.write.UpdateProfileCommand;
import com.example.short_link.profile.application.write.UpdateProfileUseCase;
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

  private final ProfileQueryService queryService;
  private final UpdateProfileUseCase updateProfile;
  private final ReorderProfileUseCase reorderProfile;

  @GetMapping
  public MyProfile myProfile(@AuthenticationPrincipal Long userId) {
    return queryService.myProfile(userId);
  }

  @PutMapping
  public MyProfile update(
      @AuthenticationPrincipal Long userId, @RequestBody UpdateRequest request) {
    return updateProfile.execute(
        new UpdateProfileCommand(
            userId, request.username(), request.bio(), request.theme(), request.socials()));
  }

  @PutMapping("/order")
  public MyProfile reorder(
      @AuthenticationPrincipal Long userId, @RequestBody ReorderRequest request) {
    reorderProfile.execute(new ReorderProfileCommand(userId, request.items()));
    return queryService.myProfile(userId);
  }

  public record UpdateRequest(
      @Size(max = 32) String username,
      @Size(max = 280) String bio,
      @Pattern(regexp = "^(light|dark|accent|sunset|ocean|forest|mono|neon|aurora|wave|ember)?$")
          String theme,
      @Size(max = 1024) String socials) {}

  public record ReorderRequest(List<ReorderItem> items) {}
}
