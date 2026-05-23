package com.example.short_link.link.application;

import com.example.short_link.link.domain.LinkDestinationEntity;
import com.example.short_link.link.domain.LinkDestinationRepository;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages A/B variants for a link. The link's {@code original_url} stays as the implicit control;
 * any rows here add weighted alternatives. Mutating operations evict the {@code link} cache so the
 * redirect path picks up the new weights immediately.
 */
@Service
@RequiredArgsConstructor
public class LinkDestinationService {

  public static final int MAX_PER_LINK = 4;
  public static final int MIN_WEIGHT = 1;
  public static final int MAX_WEIGHT = 100;

  private final LinkRepository linkRepository;
  private final LinkDestinationRepository repository;
  private final MeterRegistry meterRegistry;

  @Transactional
  @CacheEvict(value = "link", key = "#shortCode")
  public DestinationSummary add(
      Long userId,
      String shortCode,
      String url,
      Integer weight,
      String label,
      String countryCode,
      String deviceClass,
      String os) {
    LinkEntity link = ownedLink(userId, shortCode);
    if (!isValidUrl(url)) throw new IllegalArgumentException("destination url must be http(s)");
    if (repository.countByLinkId(link.getId()) >= MAX_PER_LINK) {
      throw new IllegalArgumentException(
          "too many destinations for this link (max " + MAX_PER_LINK + ")");
    }
    int w = clampWeight(weight);
    LinkDestinationEntity saved =
        repository.save(
            new LinkDestinationEntity(
                link.getId(), url.trim(), w, sanitizeLabel(label), countryCode, deviceClass, os));
    meterRegistry.counter("link.destination.added").increment();
    return toSummary(saved);
  }

  @Transactional
  @CacheEvict(value = "link", key = "#shortCode")
  public DestinationSummary update(
      Long userId,
      String shortCode,
      Long destinationId,
      String url,
      Integer weight,
      String label,
      Boolean enabled,
      String countryCode,
      String deviceClass,
      String os) {
    LinkDestinationEntity dest = ownedDestination(userId, shortCode, destinationId);
    if (url != null && !isValidUrl(url)) {
      throw new IllegalArgumentException("destination url must be http(s)");
    }
    Integer clampedWeight = weight == null ? null : clampWeight(weight);
    dest.update(
        url == null ? null : url.trim(),
        clampedWeight,
        sanitizeLabel(label),
        enabled,
        countryCode,
        deviceClass,
        os);
    return toSummary(dest);
  }

  @Transactional
  @CacheEvict(value = "link", key = "#shortCode")
  public LinkEntity setBlockedCountries(Long userId, String shortCode, String csv) {
    LinkEntity link = ownedLink(userId, shortCode);
    link.setBlockedCountries(csv);
    return link;
  }

  @Transactional
  @CacheEvict(value = "link", key = "#shortCode")
  public void delete(Long userId, String shortCode, Long destinationId) {
    LinkDestinationEntity dest = ownedDestination(userId, shortCode, destinationId);
    repository.delete(dest);
  }

  private LinkEntity ownedLink(Long userId, String shortCode) {
    LinkEntity link =
        linkRepository
            .findByShortCode(shortCode)
            .orElseThrow(() -> new LinkNotFoundException(shortCode));
    if (!link.isOwnedBy(userId)) throw new LinkNotOwnedException(shortCode);
    return link;
  }

  private LinkDestinationEntity ownedDestination(
      Long userId, String shortCode, Long destinationId) {
    LinkEntity link = ownedLink(userId, shortCode);
    LinkDestinationEntity dest =
        repository
            .findById(destinationId)
            .orElseThrow(() -> new IllegalArgumentException("destination not found"));
    if (!dest.getLinkId().equals(link.getId())) {
      throw new IllegalArgumentException("destination not found");
    }
    return dest;
  }

  private static boolean isValidUrl(String url) {
    if (url == null) return false;
    String trimmed = url.trim();
    if (trimmed.isEmpty()) return false;
    String lower = trimmed.toLowerCase(Locale.ROOT);
    return lower.startsWith("http://") || lower.startsWith("https://");
  }

  private static int clampWeight(Integer weight) {
    if (weight == null) return MIN_WEIGHT;
    return Math.max(MIN_WEIGHT, Math.min(MAX_WEIGHT, weight));
  }

  private static String sanitizeLabel(String label) {
    if (label == null) return null;
    String trimmed = label.trim();
    if (trimmed.isEmpty()) return null;
    return trimmed.length() > 40 ? trimmed.substring(0, 40) : trimmed;
  }

  private DestinationSummary toSummary(LinkDestinationEntity d) {
    return new DestinationSummary(
        d.getId(),
        d.getUrl(),
        d.getWeight(),
        d.getLabel(),
        d.isEnabled(),
        d.getCountryCode(),
        d.getDeviceClass(),
        d.getOs(),
        d.getCreatedAt());
  }

  public record DestinationSummary(
      Long id,
      String url,
      int weight,
      String label,
      boolean enabled,
      String countryCode,
      String deviceClass,
      String os,
      Instant createdAt) {}
}
