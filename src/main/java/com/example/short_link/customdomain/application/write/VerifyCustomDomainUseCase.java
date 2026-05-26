package com.example.short_link.customdomain.application.write;

import com.example.short_link.customdomain.application.dto.DomainSummary;
import com.example.short_link.customdomain.application.helper.CustomDomainPolicy;
import com.example.short_link.customdomain.domain.CustomDomainEntity;
import com.example.short_link.customdomain.exception.CustomDomainErrorCode;
import com.example.short_link.customdomain.exception.CustomDomainException;
import com.example.short_link.link.classifier.application.helper.TxtResolver;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VerifyCustomDomainUseCase {

  private final CustomDomainOwnership ownership;
  private final MeterRegistry meterRegistry;
  private final TxtResolver txtResolver;

  @Transactional
  public DomainSummary execute(Long userId, Long domainId) {
    CustomDomainEntity entity = ownership.ownedDomain(userId, domainId);
    boolean ok = checkTxtRecord(entity.getDomain(), entity.getVerificationToken());
    if (!ok) {
      entity.markCheckFailed();
      meterRegistry.counter("custom_domain.verify", "result", "failed").increment();
      throw new CustomDomainException(
          CustomDomainErrorCode.CUSTOM_DOMAIN_NOT_VERIFIED, entity.getDomain());
    }
    entity.markVerified();
    meterRegistry.counter("custom_domain.verify", "result", "ok").increment();
    return CustomDomainPolicy.toSummary(entity);
  }

  private boolean checkTxtRecord(String domain, String expectedToken) {
    for (String value : txtResolver.lookup(CustomDomainPolicy.TXT_PREFIX + domain)) {
      if (value.equals(expectedToken)) return true;
    }
    return false;
  }
}
