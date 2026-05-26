package com.example.short_link.customdomain.application.write;

import com.example.short_link.customdomain.domain.CustomDomainEntity;
import com.example.short_link.customdomain.domain.repository.CustomDomainRepository;
import com.example.short_link.customdomain.exception.CustomDomainErrorCode;
import com.example.short_link.customdomain.exception.CustomDomainException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CustomDomainOwnership {

  private final CustomDomainRepository repository;

  public CustomDomainEntity ownedDomain(Long userId, Long id) {
    CustomDomainEntity entity =
        repository
            .findById(id)
            .orElseThrow(
                () -> new CustomDomainException(CustomDomainErrorCode.CUSTOM_DOMAIN_NOT_FOUND));
    if (!entity.getUserId().equals(userId)) {
      throw new CustomDomainException(CustomDomainErrorCode.CUSTOM_DOMAIN_NOT_FOUND);
    }
    return entity;
  }
}
