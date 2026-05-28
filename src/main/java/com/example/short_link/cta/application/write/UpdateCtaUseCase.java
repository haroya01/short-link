package com.example.short_link.cta.application.write;

import com.example.short_link.cta.domain.CtaEntity;
import com.example.short_link.cta.domain.repository.CtaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateCtaUseCase {

  private final CtaOwnership ctaOwnership;
  private final CtaRepository ctaRepository;

  @Transactional
  public CtaEntity execute(UpdateCtaCommand cmd) {
    CtaEntity cta = ctaOwnership.requireOwned(cmd.userId(), cmd.ctaId());
    if (cmd.label() != null) cta.updateLabel(cmd.label());
    if (cmd.url() != null) cta.updateUrl(cmd.url());
    if (cmd.style() != null) cta.updateStyle(cmd.style());
    if (cmd.purpose() != null) cta.updatePurpose(cmd.purpose());
    return ctaRepository.save(cta);
  }
}
