package com.example.short_link.customdomain.application.write;

import com.example.short_link.customdomain.application.dto.DomainSummary;
import com.example.short_link.customdomain.application.helper.CustomDomainPolicy;
import com.example.short_link.customdomain.domain.CustomDomainEntity;
import com.example.short_link.customdomain.domain.repository.CustomDomainRepository;
import com.example.short_link.customdomain.exception.CustomDomainErrorCode;
import com.example.short_link.customdomain.exception.CustomDomainException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records the DNS outcome before the caller translates a failed check into an API error. */
@Service
@RequiredArgsConstructor
public class RecordCustomDomainVerificationUseCase {

  private final CustomDomainRepository repository;

  @Transactional
  public DomainSummary execute(Long userId, Long domainId, boolean verified) {
    CustomDomainEntity entity =
        repository
            .findByIdForUpdate(domainId)
            .filter(domain -> domain.getUserId().equals(userId))
            .orElseThrow(
                () -> new CustomDomainException(CustomDomainErrorCode.CUSTOM_DOMAIN_NOT_FOUND));
    recordOutcome(entity, verified);
    return CustomDomainPolicy.toSummary(entity);
  }

  /** An automatic check can race with deletion after the job selected its pending domains. */
  @Transactional
  public boolean executeIfPresent(Long domainId, boolean verified) {
    return repository
        .findByIdForUpdate(domainId)
        .map(
            entity -> {
              recordOutcome(entity, verified);
              return verified;
            })
        .orElse(false);
  }

  private static void recordOutcome(CustomDomainEntity entity, boolean verified) {
    if (verified) {
      entity.markVerified();
    } else {
      entity.markCheckFailed();
    }
  }
}
