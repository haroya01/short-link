package com.example.short_link.link.redirect.application;

import com.example.short_link.link.application.dto.CachedLink;

public sealed interface RedirectOutcome {

  record Redirect(CachedLink.Picked picked) implements RedirectOutcome {}

  record PasswordRequired() implements RedirectOutcome {}

  record Blocked() implements RedirectOutcome {}

  record DomainBlocked() implements RedirectOutcome {}

  record ExpiredWithMessage(String message) implements RedirectOutcome {}
}
