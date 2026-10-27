package com.example.short_link.profile.application.write;

import com.example.short_link.link.application.write.LinkProfilePlacement;
import com.example.short_link.profile.domain.repository.ProfileBlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class ProfileOrdering {

  private final LinkProfilePlacement linkPlacement;
  private final ProfileBlockRepository profileBlockRepository;

  int nextOrder(Long userId) {
    int links = linkPlacement.placedCount(userId);
    int blocks = (int) profileBlockRepository.countByUserId(userId);
    return links + blocks + 1;
  }
}
