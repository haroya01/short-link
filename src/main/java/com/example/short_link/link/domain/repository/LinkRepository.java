package com.example.short_link.link.domain.repository;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LinkRepository {

  Optional<LinkEntity> findById(Long id);

  LinkEntity save(LinkEntity link);

  void delete(LinkEntity link);

  void deleteAll(Collection<LinkEntity> links);

  long count();

  Optional<LinkEntity> findByShortCode(ShortCode shortCode);

  Optional<CachedLinkRow> findCachedLinkRowByShortCode(ShortCode shortCode);

  default Optional<LinkEntity> findByShortCode(String shortCode) {
    return findByShortCode(new ShortCode(shortCode));
  }

  List<LinkEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId);

  List<LinkEntity> findMyLinksCreatedAtPage(
      MyLinksSearchCriteria criteria,
      Instant cursorCreatedAt,
      Long cursorId,
      boolean ascending,
      int limit);

  List<LinkEntity> findMyLinksCandidates(MyLinksSearchCriteria criteria);

  long countByCreatedAtAfter(Instant since);

  long countByUserIdIsNull();

  long countByExpiresAtBefore(Instant when);

  List<LinkEntity> findTop500ByExpiresAtBeforeOrderByExpiresAtAsc(Instant when);

  List<LinkEntity> findOgRetryCandidates(int maxAttempts, Instant before, int limit);

  List<LinkEntity> findStaleOgCandidates(Instant before, int limit);

  long countByUserId(Long userId);

  List<LinkEntity> findAllByUserIdAndProfileOrderIsNotNullOrderByProfileOrderAsc(Long userId);

  List<LinkEntity> findAllByUserIdAndProfileHighlightedIsTrue(Long userId);

  Optional<LinkEntity> findFirstByUserIdAndOriginalUrl(Long userId, String originalUrl);

  List<LinkEntity> findAllByClaimTokenInAndUserIdIsNull(Collection<String> claimTokens);

  int incrementViewCountIfBelowLimit(Long linkId);

  int deleteByUserId(Long userId);

  interface CachedLinkRow {

    Long getId();

    ShortCode getShortCode();

    Long getUserId();

    String getOriginalUrl();

    Instant getExpiresAt();

    String getOgTitle();

    String getOgDescription();

    String getOgImage();

    String getBlockedCountries();

    Boolean getPasswordRequired();

    Integer getMaxViews();

    String getExpiredMessage();
  }
}
