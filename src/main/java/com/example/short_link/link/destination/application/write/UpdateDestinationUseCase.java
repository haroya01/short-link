package com.example.short_link.link.destination.application.write;

import com.example.short_link.link.destination.application.dto.DestinationSummary;
import com.example.short_link.link.destination.domain.DestinationPolicy;
import com.example.short_link.link.destination.domain.LinkDestinationEntity;
import com.example.short_link.link.destination.exception.DestinationErrorCode;
import com.example.short_link.link.destination.exception.DestinationException;
import com.example.short_link.link.domain.ShortCode;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateDestinationUseCase {

  private final LinkDestinationOwnership ownership;

  @Transactional
  @CacheEvict(value = "link", key = "#shortCode")
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
    LinkDestinationEntity dest = ownership.ownedDestination(userId, shortCode, destinationId);
    if (url != null && !DestinationPolicy.isValidUrl(url)) {
      throw new DestinationException(DestinationErrorCode.INVALID_DESTINATION_URL);
    }
    Integer clampedWeight = weight == null ? null : DestinationPolicy.clampWeight(weight);
    dest.update(
        url == null ? null : url.trim(),
        clampedWeight,
        DestinationPolicy.sanitizeLabel(label),
        enabled,
        countryCode,
        deviceClass,
        os);
    return DestinationSummary.from(dest);
  }
}
