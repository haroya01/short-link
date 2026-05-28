package com.example.short_link.customdomain.application.write;

import com.example.short_link.common.net.TxtResolver;
import com.example.short_link.customdomain.application.helper.CustomDomainPolicy;
import com.example.short_link.customdomain.domain.CustomDomainEntity;
import com.example.short_link.customdomain.domain.repository.CustomDomainRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Probe one pending domain on behalf of the auto-verify job. Never throws on DNS misses; that's the
 * normal "still propagating" path and the next tick will retry.
 */
@Service
@RequiredArgsConstructor
public class AutoVerifyCustomDomainUseCase {

  private final CustomDomainRepository repository;
  private final MeterRegistry meterRegistry;
  private final TxtResolver txtResolver;

  @Transactional
  public boolean execute(CustomDomainEntity entity) {
    boolean ok = checkTxtRecord(entity.getDomain(), entity.getVerificationToken());
    CustomDomainEntity reloaded = repository.findById(entity.getId()).orElse(null);
    if (reloaded == null) return false;
    if (ok) {
      reloaded.markVerified();
      meterRegistry.counter("custom_domain.verify", "result", "auto_ok").increment();
      return true;
    }
    reloaded.markCheckFailed();
    return false;
  }

  private boolean checkTxtRecord(String domain, String expectedToken) {
    for (String value : txtResolver.lookup(CustomDomainPolicy.TXT_PREFIX + domain)) {
      if (value.equals(expectedToken)) return true;
    }
    return false;
  }
}
