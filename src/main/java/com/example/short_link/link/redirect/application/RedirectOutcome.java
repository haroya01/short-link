package com.example.short_link.link.redirect.application;

import com.example.short_link.link.application.dto.CachedLink;

/**
 * Sealed result of a redirect flow. The presentation layer pattern-matches and renders the right
 * HTTP shape (302 / HTML interstitial / preview body) — the application layer never builds a
 * ResponseEntity itself. Hot-path errors (NotFound, Expired) keep flowing as {@link
 * com.example.short_link.link.exception.LinkException}, so the handler chain keeps owning their
 * response shape; this outcome only covers the cases where success means "render something other
 * than a 302".
 */
public sealed interface RedirectOutcome {

  /** Standard 302 to the picked destination. */
  record Redirect(CachedLink.Picked picked) implements RedirectOutcome {}

  /** Password-protected link — render the password prompt at 200. */
  record PasswordRequired() implements RedirectOutcome {}

  /** Geo-blocked for the resolved client country — render the blocked interstitial. */
  record Blocked() implements RedirectOutcome {}

  /** View limit hit but the owner set an {@code expired_message} — render that copy at 410. */
  record ExpiredWithMessage(String message) implements RedirectOutcome {}
}
