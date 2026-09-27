package com.example.short_link.link.visit.application;

import com.example.short_link.link.application.LinkCacheEviction;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.link.visit.domain.LinkVisitOptionEntity;
import com.example.short_link.link.visit.domain.repository.LinkVisitOptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LinkVisitOptionService {

  private final LinkRepository links;
  private final LinkVisitOptionRepository options;
  private final LinkCacheEviction linkCacheEviction;

  @Transactional
  public LinkVisitOptionEntity update(Long userId, ShortCode shortCode, Boolean openInBrowser) {
    LinkEntity link =
        links
            .findByShortCode(shortCode)
            .orElseThrow(() -> new LinkException(LinkErrorCode.LINK_NOT_FOUND, shortCode));
    if (!link.isOwnedBy(userId)) {
      throw new LinkException(LinkErrorCode.LINK_NOT_OWNED, shortCode);
    }
    LinkVisitOptionEntity option =
        options.findById(link.getId()).orElseGet(() -> new LinkVisitOptionEntity(link.linkId()));
    if (openInBrowser != null) {
      option.changeOpenInBrowser(openInBrowser);
    }
    linkCacheEviction.evictAfterCommit(shortCode);
    return options.save(option);
  }
}
