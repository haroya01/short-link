package com.example.short_link.link.redirect.application;

import com.example.short_link.link.application.dto.CachedLink;

/**
 * Not-found and expired errors still propagate as {@link
 * com.example.short_link.link.exception.LinkException} for the handler chain to render.
 */
public sealed interface RedirectOutcome {

  record Redirect(CachedLink.Picked picked) implements RedirectOutcome {}

  /** The password prompt is rendered with HTTP 200. */
  record PasswordRequired() implements RedirectOutcome {}

  record Blocked() implements RedirectOutcome {}

  record DomainBlocked() implements RedirectOutcome {}

  /** An owner-provided message is rendered with HTTP 410 when the view limit is reached. */
  record ExpiredWithMessage(String message) implements RedirectOutcome {}
}
