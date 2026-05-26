package com.example.short_link.customdomain.application.write;

import com.example.short_link.customdomain.domain.CustomDomainEntity;
import com.example.short_link.customdomain.domain.repository.CustomDomainRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeleteCustomDomainUseCase {

  private final CustomDomainOwnership ownership;
  private final CustomDomainRepository repository;
  private final MeterRegistry meterRegistry;

  @Transactional
  public void execute(Long userId, Long domainId) {
    CustomDomainEntity entity = ownership.ownedDomain(userId, domainId);
    repository.delete(entity);
    meterRegistry.counter("custom_domain.deleted").increment();
  }
}
