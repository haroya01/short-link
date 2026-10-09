package com.example.short_link.link.destination.application.write;

import com.example.short_link.link.application.LinkCacheEviction;
import com.example.short_link.link.application.write.CreateLinkValidator;
import com.example.short_link.link.destination.application.dto.DestinationSummary;
import com.example.short_link.link.destination.domain.DestinationPolicy;
import com.example.short_link.link.destination.domain.LinkDestinationEntity;
import com.example.short_link.link.destination.domain.repository.LinkDestinationRepository;
import com.example.short_link.link.destination.exception.DestinationErrorCode;
import com.example.short_link.link.destination.exception.DestinationException;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class AddDestinationUseCase {

  public static final int MAX_PER_LINK = 4;
  public static final int MIN_WEIGHT = DestinationPolicy.MIN_WEIGHT;
  public static final int MAX_WEIGHT = DestinationPolicy.MAX_WEIGHT;

  private final LinkDestinationOwnership ownership;
  private final LinkDestinationRepository repository;
  private final MeterRegistry meterRegistry;
  private final LinkCacheEviction linkCacheEviction;
  private final CreateLinkValidator urlValidator;
  private final TransactionTemplate transaction;

  public DestinationSummary execute(
      Long userId,
      ShortCode shortCode,
      String url,
      Integer weight,
      String label,
      String countryCode,
      String deviceClass,
      String os) {
    if (!DestinationPolicy.isValidUrl(url)) {
      throw new DestinationException(DestinationErrorCode.INVALID_DESTINATION_URL);
    }
    // Safe Browsing HTTP calls stay outside the transaction to avoid holding a JDBC connection.
    urlValidator.validateUrl(url.trim());
    return transaction.execute(
        status -> add(userId, shortCode, url.trim(), weight, label, countryCode, deviceClass, os));
  }

  private DestinationSummary add(
      Long userId,
      ShortCode shortCode,
      String url,
      Integer weight,
      String label,
      String countryCode,
      String deviceClass,
      String os) {
    LinkEntity link = ownership.ownedLink(userId, shortCode);
    if (repository.countByLinkId(link.linkId().value()) >= MAX_PER_LINK) {
      throw new DestinationException(DestinationErrorCode.TOO_MANY_DESTINATIONS, MAX_PER_LINK);
    }
    int w = DestinationPolicy.clampWeight(weight);
    LinkDestinationEntity saved =
        repository.save(
            new LinkDestinationEntity(
                link.linkId(),
                url,
                w,
                DestinationPolicy.sanitizeLabel(label),
                countryCode,
                deviceClass,
                os));
    meterRegistry.counter("link.destination.added").increment();
    linkCacheEviction.evictAfterCommit(shortCode);
    return DestinationSummary.from(saved);
  }
}
