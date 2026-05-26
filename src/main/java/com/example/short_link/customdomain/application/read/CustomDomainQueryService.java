package com.example.short_link.customdomain.application.read;

import com.example.short_link.customdomain.application.dto.DomainSummary;
import com.example.short_link.customdomain.application.helper.CustomDomainPolicy;
import com.example.short_link.customdomain.domain.CustomDomainEntity;
import com.example.short_link.customdomain.domain.repository.CustomDomainRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomDomainQueryService {

  private final CustomDomainRepository repository;

  @Transactional(readOnly = true)
  public List<DomainSummary> list(Long userId) {
    return repository.findAllByUserIdOrderByIdAsc(userId).stream()
        .map(CustomDomainPolicy::toSummary)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<CustomDomainEntity> findPendingWithinWindow() {
    Instant cutoff = Instant.now().minus(CustomDomainPolicy.AUTO_VERIFY_WINDOW);
    return repository.findAllByVerifiedFalseAndCreatedAtAfter(cutoff);
  }

  @Transactional(readOnly = true)
  public Long resolveOwner(String hostHeader) {
    if (hostHeader == null || hostHeader.isBlank()) return null;
    String domain = CustomDomainPolicy.normalize(hostHeader);
    return repository
        .findByDomain(domain)
        .filter(CustomDomainEntity::isVerified)
        .map(CustomDomainEntity::getUserId)
        .orElse(null);
  }
}
