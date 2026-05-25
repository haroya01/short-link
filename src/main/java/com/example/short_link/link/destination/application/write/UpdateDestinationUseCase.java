package com.example.short_link.link.destination.application.write;

import com.example.short_link.link.destination.application.dto.DestinationSummary;
import com.example.short_link.link.destination.domain.LinkDestinationEntity;
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
      String shortCode,
      Long destinationId,
      String url,
      Integer weight,
      String label,
      Boolean enabled,
      String countryCode,
      String deviceClass,
      String os) {
    LinkDestinationEntity dest = ownership.ownedDestination(userId, shortCode, destinationId);
    if (url != null && !AddDestinationUseCase.isValidUrl(url)) {
      throw new IllegalArgumentException("destination url must be http(s)");
    }
    Integer clampedWeight = weight == null ? null : AddDestinationUseCase.clampWeight(weight);
    dest.update(
        url == null ? null : url.trim(),
        clampedWeight,
        AddDestinationUseCase.sanitizeLabel(label),
        enabled,
        countryCode,
        deviceClass,
        os);
    return AddDestinationUseCase.toSummary(dest);
  }
}
