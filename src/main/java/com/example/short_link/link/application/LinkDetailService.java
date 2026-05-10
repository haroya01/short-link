package com.example.short_link.link.application;

import com.example.short_link.link.api.LinkDetailController.LinkDetailResponse;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LinkDetailService {

  private final LinkRepository repository;
  private final LinkTagService linkTagService;
  private final LinkAccessGuard accessGuard;

  @Transactional(readOnly = true)
  public LinkDetailResponse detail(Long userId, String shortCode) {
    LinkEntity link =
        repository
            .findByShortCode(shortCode)
            .orElseThrow(() -> new LinkNotFoundException(shortCode));
    accessGuard.requireView(userId, link);
    return new LinkDetailResponse(
        link.getShortCode(),
        link.getOriginalUrl(),
        link.getExpiresAt(),
        link.getOgTitle(),
        link.getOgDescription(),
        link.getOgImage(),
        link.getOgTitleOverride(),
        link.getOgDescriptionOverride(),
        link.getOgImageOverride(),
        link.hasPassword(),
        link.getMaxViews(),
        link.getViewCount(),
        link.isStatsPublic(),
        linkTagService.tagNamesFor(userId, shortCode),
        link.getNote(),
        link.getExpiredMessage());
  }
}
