package com.example.short_link.customdomain.application.write;

import com.example.short_link.customdomain.application.dto.DomainSummary;
import com.example.short_link.customdomain.application.helper.CustomDomainPolicy;
import com.example.short_link.customdomain.domain.CustomDomainEntity;
import com.example.short_link.customdomain.domain.repository.CustomDomainRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.security.SecureRandom;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegisterCustomDomainUseCase {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final HexFormat HEX = HexFormat.of();

  private final CustomDomainRepository repository;
  private final MeterRegistry meterRegistry;

  @Transactional
  public DomainSummary execute(Long userId, String rawDomain) {
    String domain = CustomDomainPolicy.normalize(rawDomain);
    CustomDomainPolicy.validate(domain);
    if (repository.existsByDomain(domain)) {
      throw new IllegalArgumentException("domain already registered");
    }
    if (repository.findAllByUserIdOrderByIdAsc(userId).size() >= CustomDomainPolicy.MAX_PER_USER) {
      throw new IllegalArgumentException(
          "max " + CustomDomainPolicy.MAX_PER_USER + " custom domains per user");
    }
    String token = "kurl-verify=" + HEX.formatHex(randomBytes(16));
    CustomDomainEntity saved = repository.save(new CustomDomainEntity(userId, domain, token));
    meterRegistry.counter("custom_domain.registered").increment();
    return CustomDomainPolicy.toSummary(saved);
  }

  private static byte[] randomBytes(int n) {
    byte[] buf = new byte[n];
    RANDOM.nextBytes(buf);
    return buf;
  }
}
