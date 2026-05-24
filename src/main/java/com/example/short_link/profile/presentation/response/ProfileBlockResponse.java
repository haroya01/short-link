package com.example.short_link.profile.presentation.response;

import com.example.short_link.profile.domain.ProfileBlockEntity;

public record ProfileBlockResponse(Long id, String type, String content, Integer profileOrder) {

  public static ProfileBlockResponse from(ProfileBlockEntity block) {
    return new ProfileBlockResponse(
        block.getId(), block.getType().name(), block.getContent(), block.getProfileOrder());
  }
}
