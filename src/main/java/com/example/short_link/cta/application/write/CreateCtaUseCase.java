package com.example.short_link.cta.application.write;

import com.example.short_link.cta.domain.CtaEntity;
import com.example.short_link.cta.domain.repository.CtaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateCtaUseCase {

  private final CtaRepository ctaRepository;
  private final CtaLinkTracker linkTracker;

  @Transactional
  public CtaEntity execute(CreateCtaCommand cmd) {
    CtaEntity cta = new CtaEntity(cmd.userId(), cmd.label(), cmd.url(), cmd.style(), cmd.purpose());
    cta.trackVia(linkTracker.trackingCodeFor(cmd.userId(), cmd.url()));
    return ctaRepository.save(cta);
  }
}
