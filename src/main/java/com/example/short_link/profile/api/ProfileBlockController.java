package com.example.short_link.profile.api;

import com.example.short_link.profile.api.request.ProfileBlockCreateRequest;
import com.example.short_link.profile.api.request.ProfileBlockUpdateRequest;
import com.example.short_link.profile.api.response.ProfileBlockResponse;
import com.example.short_link.profile.application.write.CreateBlockCommand;
import com.example.short_link.profile.application.write.CreateBlockUseCase;
import com.example.short_link.profile.application.write.DeleteBlockCommand;
import com.example.short_link.profile.application.write.DeleteBlockUseCase;
import com.example.short_link.profile.application.write.UpdateBlockCommand;
import com.example.short_link.profile.application.write.UpdateBlockUseCase;
import com.example.short_link.profile.domain.ProfileBlockEntity;
import com.example.short_link.profile.domain.ProfileBlockType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/profile/blocks")
@RequiredArgsConstructor
public class ProfileBlockController {

  private final CreateBlockUseCase createBlock;
  private final UpdateBlockUseCase updateBlock;
  private final DeleteBlockUseCase deleteBlock;

  @PostMapping
  public ProfileBlockResponse create(
      @AuthenticationPrincipal Long userId, @RequestBody ProfileBlockCreateRequest request) {
    ProfileBlockType type = ProfileBlockType.valueOf(request.type().toUpperCase());
    ProfileBlockEntity block =
        createBlock.execute(new CreateBlockCommand(userId, type, request.content()));
    return ProfileBlockResponse.from(block);
  }

  @PatchMapping("/{id}")
  public ProfileBlockResponse update(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long id,
      @RequestBody ProfileBlockUpdateRequest request) {
    ProfileBlockEntity block =
        updateBlock.execute(new UpdateBlockCommand(userId, id, request.content()));
    return ProfileBlockResponse.from(block);
  }

  @DeleteMapping("/{id}")
  public void delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    deleteBlock.execute(new DeleteBlockCommand(userId, id));
  }
}
