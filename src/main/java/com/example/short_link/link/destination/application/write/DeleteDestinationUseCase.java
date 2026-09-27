package com.example.short_link.link.destination.application.write;

import com.example.short_link.link.application.LinkCacheEviction;
import com.example.short_link.link.destination.domain.LinkDestinationEntity;
import com.example.short_link.link.destination.domain.repository.LinkDestinationRepository;
import com.example.short_link.link.domain.ShortCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeleteDestinationUseCase {

  private final LinkDestinationOwnership ownership;
  private final LinkDestinationRepository repository;
  private final LinkCacheEviction linkCacheEviction;

  @Transactional
  public void execute(Long userId, ShortCode shortCode, Long destinationId) {
    LinkDestinationEntity dest = ownership.ownedDestination(userId, shortCode, destinationId);
    repository.delete(dest);
    linkCacheEviction.evictAfterCommit(shortCode);
  }
}
