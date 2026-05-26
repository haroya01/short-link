package com.example.short_link.link.destination.application.write;

import com.example.short_link.link.destination.application.dto.DestinationSummary;
import com.example.short_link.link.destination.domain.LinkDestinationEntity;
import com.example.short_link.link.destination.domain.repository.LinkDestinationRepository;
import com.example.short_link.link.destination.exception.DestinationErrorCode;
import com.example.short_link.link.destination.exception.DestinationException;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AddDestinationUseCase {

  public static final int MAX_PER_LINK = 4;
  public static final int MIN_WEIGHT = 1;
  public static final int MAX_WEIGHT = 100;

  private final LinkDestinationOwnership ownership;
  private final LinkDestinationRepository repository;
  private final MeterRegistry meterRegistry;

  @Transactional
  @CacheEvict(value = "link", key = "#shortCode")
  public DestinationSummary execute(
      Long userId,
      ShortCode shortCode,
      String url,
      Integer weight,
      String label,
      String countryCode,
      String deviceClass,
      String os) {
    LinkEntity link = ownership.ownedLink(userId, shortCode);
    if (!isValidUrl(url)) {
      throw new DestinationException(DestinationErrorCode.INVALID_DESTINATION_URL);
    }
    if (repository.countByLinkId(link.linkId()) >= MAX_PER_LINK) {
      throw new DestinationException(DestinationErrorCode.TOO_MANY_DESTINATIONS, MAX_PER_LINK);
    }
    int w = clampWeight(weight);
    LinkDestinationEntity saved =
        repository.save(
            new LinkDestinationEntity(
                link.linkId(), url.trim(), w, sanitizeLabel(label), countryCode, deviceClass, os));
    meterRegistry.counter("link.destination.added").increment();
    return toSummary(saved);
  }

  static boolean isValidUrl(String url) {
    if (url == null) return false;
    String trimmed = url.trim();
    if (trimmed.isEmpty()) return false;
    String lower = trimmed.toLowerCase(Locale.ROOT);
    return lower.startsWith("http://") || lower.startsWith("https://");
  }

  static int clampWeight(Integer weight) {
    if (weight == null) return MIN_WEIGHT;
    return Math.max(MIN_WEIGHT, Math.min(MAX_WEIGHT, weight));
  }

  static String sanitizeLabel(String label) {
    if (label == null) return null;
    String trimmed = label.trim();
    if (trimmed.isEmpty()) return null;
    return trimmed.length() > 40 ? trimmed.substring(0, 40) : trimmed;
  }

  static DestinationSummary toSummary(LinkDestinationEntity d) {
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
}
