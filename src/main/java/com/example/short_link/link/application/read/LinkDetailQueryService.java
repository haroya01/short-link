package com.example.short_link.link.application.read;

import com.example.short_link.link.access.application.LinkAccessGuard;
import com.example.short_link.link.application.dto.LinkDetailView;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.tag.application.read.LinkTagQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LinkDetailQueryService {

  private final LinkRepository repository;
  private final LinkTagQueryService linkTagService;
  private final LinkAccessGuard accessGuard;

  @Transactional(readOnly = true)
  public LinkDetailView detail(Long userId, String shortCode) {
    LinkEntity link =
        repository
            .findByShortCode(shortCode)
            .orElseThrow(() -> new LinkException(LinkErrorCode.LINK_NOT_FOUND, shortCode));
    accessGuard.requireView(userId, link);
    return new LinkDetailView(
        link.getShortCode().value(),
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
