package com.example.short_link.cta.application.write;

import com.example.short_link.cta.domain.CtaEntity;
import com.example.short_link.cta.domain.repository.CtaRepository;
import com.example.short_link.link.visit.application.SplashCtaChanges;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class UpdateCtaUseCase {

  private final CtaOwnership ctaOwnership;
  private final CtaRepository ctaRepository;
  private final CtaLinkTracker linkTracker;
  private final SplashCtaChanges splashCtaChanges;
  private final TransactionTemplate transaction;

  public CtaEntity execute(UpdateCtaCommand cmd) {
    CtaLinkTracker.Tracking tracking =
        cmd.url() == null ? CtaLinkTracker.Tracking.NONE : trackingFor(cmd);
    return transaction.execute(status -> update(cmd, tracking));
  }

  private CtaLinkTracker.Tracking trackingFor(UpdateCtaCommand cmd) {
    ctaOwnership.requireOwned(cmd.userId(), cmd.ctaId());
    return linkTracker.plan(cmd.userId(), cmd.url());
  }

  private CtaEntity update(UpdateCtaCommand cmd, CtaLinkTracker.Tracking tracking) {
    CtaEntity cta = ctaOwnership.requireOwned(cmd.userId(), cmd.ctaId());
    if (cmd.label() != null) cta.updateLabel(cmd.label());
    if (cmd.url() != null) {
      cta.updateUrl(cmd.url());
      cta.trackVia(linkTracker.trackingCodeFor(tracking));
    }
    if (cmd.style() != null) cta.updateStyle(cmd.style());
    if (cmd.purpose() != null) cta.updatePurpose(cmd.purpose());
    CtaEntity saved = ctaRepository.save(cta);
    splashCtaChanges.ctaChanged(saved.getId());
    return saved;
  }
}
