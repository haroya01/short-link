package com.example.short_link.cta.application.write;

import com.example.short_link.cta.domain.CtaEntity;
import com.example.short_link.cta.domain.repository.CtaRepository;
import com.example.short_link.cta.exception.CtaErrorCode;
import com.example.short_link.cta.exception.CtaException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CtaOwnership {

  private final CtaRepository ctaRepository;

  public CtaEntity requireOwned(Long userId, Long ctaId) {
    CtaEntity cta =
        ctaRepository
            .findById(ctaId)
            .orElseThrow(() -> new CtaException(CtaErrorCode.CTA_NOT_FOUND, ctaId));
    if (!cta.isOwnedBy(userId)) {
      throw new CtaException(CtaErrorCode.CTA_PERMISSION_DENIED).with("ctaId", ctaId);
    }
    return cta;
  }
}
