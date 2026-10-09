package com.example.short_link.link.destination.application.write;

import com.example.short_link.link.application.LinkCacheEviction;
import com.example.short_link.link.application.write.CreateLinkValidator;
import com.example.short_link.link.destination.application.dto.DestinationSummary;
import com.example.short_link.link.destination.domain.DestinationPolicy;
import com.example.short_link.link.destination.domain.LinkDestinationEntity;
import com.example.short_link.link.destination.exception.DestinationErrorCode;
import com.example.short_link.link.destination.exception.DestinationException;
import com.example.short_link.link.domain.ShortCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class UpdateDestinationUseCase {

  private final LinkDestinationOwnership ownership;
  private final LinkCacheEviction linkCacheEviction;
  private final CreateLinkValidator urlValidator;
  private final TransactionTemplate transaction;

  public DestinationSummary execute(
      Long userId,
      ShortCode shortCode,
      Long destinationId,
      String url,
      Integer weight,
      String label,
      Boolean enabled,
      String countryCode,
      String deviceClass,
      String os) {
    if (url != null && !DestinationPolicy.isValidUrl(url)) {
      throw new DestinationException(DestinationErrorCode.INVALID_DESTINATION_URL);
    }
    // Safe Browsing HTTP calls stay outside the transaction to avoid holding a JDBC connection.
    if (url != null) {
      urlValidator.validateUrl(url.trim());
    }
    return transaction.execute(
        status ->
            update(
                userId,
                shortCode,
                destinationId,
                url,
                weight,
                label,
                enabled,
                countryCode,
                deviceClass,
                os));
  }

  private DestinationSummary update(
      Long userId,
      ShortCode shortCode,
      Long destinationId,
      String url,
      Integer weight,
      String label,
      Boolean enabled,
      String countryCode,
      String deviceClass,
      String os) {
    LinkDestinationEntity dest = ownership.ownedDestination(userId, shortCode, destinationId);
    Integer clampedWeight = weight == null ? null : DestinationPolicy.clampWeight(weight);
    dest.update(
        url == null ? null : url.trim(),
        clampedWeight,
        DestinationPolicy.sanitizeLabel(label),
        enabled,
        countryCode,
        deviceClass,
        os);
    linkCacheEviction.evictAfterCommit(shortCode);
    return DestinationSummary.from(dest);
  }
}
