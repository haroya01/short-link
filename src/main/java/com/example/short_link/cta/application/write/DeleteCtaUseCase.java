package com.example.short_link.cta.application.write;

import com.example.short_link.cta.domain.CtaEntity;
import com.example.short_link.cta.domain.repository.CtaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeleteCtaUseCase {

  private final CtaOwnership ctaOwnership;
  private final CtaRepository ctaRepository;

  @Transactional
  public void execute(DeleteCtaCommand cmd) {
    CtaEntity cta = ctaOwnership.requireOwned(cmd.userId(), cmd.ctaId());
    cta.softDelete();
    ctaRepository.save(cta);
  }
}
