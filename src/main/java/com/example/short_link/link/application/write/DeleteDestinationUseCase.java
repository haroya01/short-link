package com.example.short_link.link.application.write;

import com.example.short_link.link.domain.LinkDestinationEntity;
import com.example.short_link.link.domain.LinkDestinationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeleteDestinationUseCase {

  private final LinkDestinationOwnership ownership;
  private final LinkDestinationRepository repository;

  @Transactional
  @CacheEvict(value = "link", key = "#shortCode")
  public void execute(Long userId, String shortCode, Long destinationId) {
    LinkDestinationEntity dest = ownership.ownedDestination(userId, shortCode, destinationId);
    repository.delete(dest);
  }
}
