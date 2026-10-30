package com.example.short_link.cta.application.write;

import com.example.short_link.cta.domain.CtaEntity;
import com.example.short_link.cta.domain.repository.CtaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class CreateCtaUseCase {

  private final CtaRepository ctaRepository;
  private final CtaLinkTracker linkTracker;
  private final TransactionTemplate transaction;

  public CtaEntity execute(CreateCtaCommand cmd) {
    CtaEntity cta = new CtaEntity(cmd.userId(), cmd.label(), cmd.url(), cmd.style(), cmd.purpose());
    CtaLinkTracker.Tracking tracking = linkTracker.plan(cmd.userId(), cmd.url());
    return transaction.execute(
        status -> {
          cta.trackVia(linkTracker.trackingCodeFor(tracking));
          return ctaRepository.save(cta);
        });
  }
}
