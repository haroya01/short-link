package com.example.short_link.link.application;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.stats.domain.ClickEventEntity;
import com.example.short_link.link.stats.domain.repository.ClickEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LinkAccountData {

  private final LinkRepository links;
  private final ClickEventRepository clickEvents;
  private final LinkCacheEviction linkCacheEviction;

  public long countOwned(Long userId) {
    return links.countByUserId(userId);
  }

  // Runs after the slice erasers: campaign, event and CTA rows point at these links.
  public int eraseOwned(Long userId) {
    List<LinkEntity> owned = links.findAllByUserIdOrderByCreatedAtDesc(userId);
    if (!owned.isEmpty()) {
      List<Long> linkIds = owned.stream().map(LinkEntity::getId).toList();
      int deletedClicks = clickEvents.deleteByLinkIds(linkIds);
      log.info("user {} hard-delete: {} click events removed", userId, deletedClicks);
    }
    int deletedLinks = links.deleteByUserId(userId);
    linkCacheEviction.evictAllAfterCommit(owned.stream().map(LinkEntity::getShortCode).toList());
    return deletedLinks;
  }

  public OwnedLinks export(Long userId) {
    List<LinkEntity> owned = links.findAllByUserIdOrderByCreatedAtDesc(userId);
    return new OwnedLinks(owned.stream().map(ExportedLink::from).toList(), exportClicks(owned));
  }

  private List<ExportedClick> exportClicks(List<LinkEntity> owned) {
    if (owned.isEmpty()) return List.of();
    Map<Long, String> shortCodeByLinkId =
        owned.stream().collect(Collectors.toMap(LinkEntity::getId, l -> l.getShortCode().value()));
    List<Long> linkIds = List.copyOf(shortCodeByLinkId.keySet());
    return clickEvents.findAllByLinkIdInOrderByClickedAtAsc(linkIds).stream()
        .map(c -> ExportedClick.from(c, shortCodeByLinkId.get(c.getLinkId())))
        .toList();
  }

  public record OwnedLinks(List<ExportedLink> links, List<ExportedClick> clicks) {}

  public record ExportedLink(
      String shortCode,
      String originalUrl,
      Instant createdAt,
      Instant expiresAt,
      String ogTitle,
      String ogDescription,
      String ogImage) {

    static ExportedLink from(LinkEntity l) {
      return new ExportedLink(
          l.getShortCode().value(),
          l.getOriginalUrl(),
          l.getCreatedAt(),
          l.getExpiresAt(),
          l.getOgTitle(),
          l.getOgDescription(),
          l.getOgImage());
    }
  }

  public record ExportedClick(
      String shortCode,
      Instant clickedAt,
      String referrerHost,
      String deviceClass,
      String osName,
      String browserName,
      String countryCode,
      String regionName,
      String cityName,
      String language,
      boolean bot,
      String botName) {

    static ExportedClick from(ClickEventEntity c, String shortCode) {
      return new ExportedClick(
          shortCode,
          c.getClickedAt(),
          c.getReferrerHost(),
          c.getDeviceClass(),
          c.getOsName(),
          c.getBrowserName(),
          c.getCountryCode(),
          c.getRegionName(),
          c.getCityName(),
          c.getLanguage(),
          c.isBot(),
          c.getBotName());
    }
  }
}
