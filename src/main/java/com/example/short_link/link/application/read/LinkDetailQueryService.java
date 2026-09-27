package com.example.short_link.link.application.read;

import com.example.short_link.link.access.application.LinkAccessGuard;
import com.example.short_link.link.application.dto.LinkDetailView;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.link.visit.domain.LinkVisitOptionEntity;
import com.example.short_link.link.visit.domain.repository.LinkVisitOptionRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LinkDetailQueryService {

  private final LinkRepository repository;
  private final LinkTagLookup linkTagService;
  private final LinkAccessGuard accessGuard;
  private final LinkVisitOptionRepository visitOptions;

  @Transactional(readOnly = true)
  public LinkDetailView detail(Long userId, ShortCode shortCode) {
    LinkEntity link =
        repository
            .findByShortCode(shortCode)
            .orElseThrow(() -> new LinkException(LinkErrorCode.LINK_NOT_FOUND, shortCode));
    accessGuard.requireView(userId, link);
    Optional<LinkVisitOptionEntity> option = visitOptions.findById(link.getId());
    return new LinkDetailView(
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
        link.getExpiredMessage(),
        option.map(LinkVisitOptionEntity::isOpenInBrowser).orElse(false),
        option
            .map(
                o ->
                    new LinkDetailView.Splash(
                        o.isSplashEnabled(),
                        o.getSplashMessage(),
                        o.getSplashSeconds(),
                        o.getSplashCtaId()))
            .orElse(LinkDetailView.Splash.OFF));
  }
}
