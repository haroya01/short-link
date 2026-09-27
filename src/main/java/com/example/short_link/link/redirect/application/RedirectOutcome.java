package com.example.short_link.link.redirect.application;

import com.example.short_link.link.application.dto.CachedLink;
import java.time.Instant;

public sealed interface RedirectOutcome {

  record Redirect(CachedLink.Picked picked, CachedLink.VisitOptions visitOptions)
      implements RedirectOutcome {

    public Redirect {
      visitOptions = visitOptions == null ? CachedLink.VisitOptions.NONE : visitOptions;
    }

    public Redirect(CachedLink.Picked picked) {
      this(picked, CachedLink.VisitOptions.NONE);
    }
  }

  record PasswordRequired() implements RedirectOutcome {}

  record Blocked() implements RedirectOutcome {}

  record DomainBlocked() implements RedirectOutcome {}

  record ExpiredWithMessage(String message) implements RedirectOutcome {}

  record NotYetOpen(Instant opensAt) implements RedirectOutcome {}
}
