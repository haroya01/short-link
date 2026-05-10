package com.example.short_link.profile.api;

import com.example.short_link.profile.application.ProfileService;
import com.example.short_link.profile.domain.ProfileBlockEntity;
import com.example.short_link.profile.domain.ProfileBlockType;
import jakarta.validation.constraints.Size;
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

  private final ProfileService service;

  @PostMapping
  public BlockResponse create(
      @AuthenticationPrincipal Long userId, @RequestBody CreateRequest request) {
    ProfileBlockType type = ProfileBlockType.valueOf(request.type().toUpperCase());
    ProfileBlockEntity block = service.createBlock(userId, type, request.content());
    return BlockResponse.from(block);
  }

  @PatchMapping("/{id}")
  public BlockResponse update(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long id,
      @RequestBody UpdateRequest request) {
    ProfileBlockEntity block = service.updateBlock(userId, id, request.content());
    return BlockResponse.from(block);
  }

  @DeleteMapping("/{id}")
  public void delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    service.deleteBlock(userId, id);
  }

  public record CreateRequest(String type, @Size(max = 120) String content) {}

  public record UpdateRequest(@Size(max = 120) String content) {}

  public record BlockResponse(Long id, String type, String content, Integer profileOrder) {
    static BlockResponse from(ProfileBlockEntity block) {
      return new BlockResponse(
          block.getId(), block.getType().name(), block.getContent(), block.getProfileOrder());
    }
  }
}
