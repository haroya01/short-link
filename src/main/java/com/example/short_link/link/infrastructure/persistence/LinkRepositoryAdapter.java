package com.example.short_link.link.infrastructure.persistence;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.domain.repository.MyLinksSearchCriteria;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class LinkRepositoryAdapter implements LinkRepository {

  private final JpaLinkRepository jpa;

  @Override
  public Optional<LinkEntity> findById(Long id) {
    return jpa.findById(id);
  }

  @Override
  public LinkEntity save(LinkEntity link) {
    return jpa.save(link);
  }

  @Override
  public void delete(LinkEntity link) {
    jpa.delete(link);
  }

  @Override
  public void deleteAll(Collection<LinkEntity> links) {
    jpa.deleteAll(links);
  }

  @Override
  public long count() {
    return jpa.count();
  }

  @Override
  public Optional<LinkEntity> findByShortCode(ShortCode shortCode) {
    return jpa.findByShortCode(shortCode);
  }

  @Override
  public List<LinkEntity> findAllByShortCodeInAndUserId(
      Collection<ShortCode> shortCodes, Long userId) {
    return jpa.findAllByShortCodeInAndUserId(shortCodes, userId);
  }

  @Override
  public Optional<CachedLinkRow> findCachedLinkRowByShortCode(ShortCode shortCode) {
    return jpa.findCachedLinkRowByShortCode(shortCode);
  }

  @Override
  public List<LinkEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId) {
    return jpa.findAllByUserIdOrderByCreatedAtDesc(userId);
  }

  @Override
  public List<LinkEntity> findMyLinksCreatedAtPage(
      MyLinksSearchCriteria criteria,
      Instant cursorCreatedAt,
      Long cursorId,
      boolean ascending,
      int limit) {
    Specification<LinkEntity> spec = MyLinksJpaSpecifications.from(criteria);
    spec = and(spec, MyLinksJpaSpecifications.cursorAfter(cursorCreatedAt, cursorId, ascending));
    return jpa.findAll(spec, PageRequest.of(0, limit, createdAtSort(ascending))).getContent();
  }

  @Override
  public List<LinkEntity> findMyLinksCandidates(MyLinksSearchCriteria criteria) {
    return jpa.findAll(MyLinksJpaSpecifications.from(criteria), defaultSort());
  }

  @Override
  public long countByCreatedAtAfter(Instant since) {
    return jpa.countByCreatedAtAfter(since);
  }

  @Override
  public long countByUserIdIsNull() {
    return jpa.countByUserIdIsNull();
  }

  @Override
  public long countByExpiresAtBefore(Instant when) {
    return jpa.countByExpiresAtBefore(when);
  }

  @Override
  public List<LinkEntity> findTop500ByExpiresAtBeforeOrderByExpiresAtAsc(Instant when) {
    return jpa.findTop500ByExpiresAtBeforeOrderByExpiresAtAsc(when);
  }

  @Override
  public List<Long> findDistinctUserIds() {
    return jpa.findDistinctUserIds();
  }

  @Override
  public List<LinkEntity> findByExpiresAtBetween(Instant from, Instant to) {
    return jpa.findByExpiresAtBetween(from, to);
  }

  @Override
  public List<LinkEntity> findOgRetryCandidates(int maxAttempts, Instant before, int limit) {
    return jpa.findOgRetryCandidates(maxAttempts, before, PageRequest.ofSize(limit));
  }

  @Override
  public List<LinkEntity> findStaleOgCandidates(Instant before, int limit) {
    return jpa.findStaleOgCandidates(before, PageRequest.ofSize(limit));
  }

  @Override
  public long countByUserId(Long userId) {
    return jpa.countByUserId(userId);
  }

  @Override
  public List<LinkEntity> findAllByUserIdAndProfileOrderIsNotNullOrderByProfileOrderAsc(
      Long userId) {
    return jpa.findAllByUserIdAndProfileOrderIsNotNullOrderByProfileOrderAsc(userId);
  }

  @Override
  public List<LinkEntity> findAllByUserIdAndProfileHighlightedIsTrue(Long userId) {
    return jpa.findAllByUserIdAndProfileHighlightedIsTrue(userId);
  }

  @Override
  public Optional<LinkEntity> findFirstByUserIdAndOriginalUrl(Long userId, String originalUrl) {
    return jpa.findFirstByUserIdAndOriginalUrl(userId, originalUrl);
  }

  @Override
  public List<LinkEntity> findAllByClaimTokenInAndUserIdIsNull(Collection<String> claimTokens) {
    return jpa.findAllByClaimTokenInAndUserIdIsNull(claimTokens);
  }

  @Override
  public int incrementViewCountIfBelowLimit(Long linkId) {
    return jpa.incrementViewCountIfBelowLimit(linkId);
  }

  @Override
  public int deleteByUserId(Long userId) {
    return jpa.deleteByUserId(userId);
  }

  private static Sort defaultSort() {
    return Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));
  }

  private static Sort createdAtSort(boolean ascending) {
    Sort.Direction direction = ascending ? Sort.Direction.ASC : Sort.Direction.DESC;
    return Sort.by(direction, "createdAt").and(Sort.by(direction, "id"));
  }

  private static Specification<LinkEntity> and(
      Specification<LinkEntity> base, Specification<LinkEntity> piece) {
    return piece == null ? base : base.and(piece);
  }
}
