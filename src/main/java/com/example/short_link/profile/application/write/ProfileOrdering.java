package com.example.short_link.profile.application.write;

import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.profile.domain.repository.ProfileBlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class ProfileOrdering {

  private final LinkRepository linkRepository;
  private final ProfileBlockRepository profileBlockRepository;

  int nextOrder(Long userId) {
    int links =
        linkRepository.findAllByUserIdAndProfileOrderIsNotNullOrderByProfileOrderAsc(userId).size();
    int blocks = (int) profileBlockRepository.countByUserId(userId);
    return links + blocks + 1;
  }
}
