package com.example.short_link.customdomain.application.write;

import com.example.short_link.common.net.TxtResolver;
import com.example.short_link.customdomain.application.helper.CustomDomainPolicy;
import com.example.short_link.customdomain.domain.CustomDomainEntity;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** DNS 불일치는 전파 지연일 수 있으므로 예외 없이 다음 자동 검증에서 재시도한다. */
@Service
@RequiredArgsConstructor
public class AutoVerifyCustomDomainUseCase {

  private final MeterRegistry meterRegistry;
  private final TxtResolver txtResolver;
  private final RecordCustomDomainVerificationUseCase recordVerification;

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public boolean execute(CustomDomainEntity entity) {
    boolean ok = checkTxtRecord(entity.getDomain(), entity.getVerificationToken());
    boolean recorded = recordVerification.executeIfPresent(entity.getId(), ok);
    if (recorded) {
      meterRegistry.counter("custom_domain.verify", "result", "auto_ok").increment();
    }
    return recorded;
  }

  private boolean checkTxtRecord(String domain, String expectedToken) {
    for (String value : txtResolver.lookup(CustomDomainPolicy.TXT_PREFIX + domain)) {
      if (value.equals(expectedToken)) return true;
    }
    return false;
  }
}
