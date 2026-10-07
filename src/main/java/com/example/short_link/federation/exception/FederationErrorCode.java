package com.example.short_link.federation.exception;

import org.springframework.http.HttpStatus;

public enum FederationErrorCode {
  REMOTE_ACCOUNT_INVALID(HttpStatus.BAD_REQUEST, "an account on another server is user@server"),
  REMOTE_ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "no account on another server: %s"),
  FEDERATION_DISABLED(
      HttpStatus.CONFLICT, "turn on federation to follow accounts on other servers"),
  REMOTE_DOMAIN_INVALID(HttpStatus.BAD_REQUEST, "a server is a domain like mastodon.social"),
  REMOTE_DOMAIN_BLOCKED(HttpStatus.CONFLICT, "you blocked %s");

  private final HttpStatus status;
  private final String template;

  FederationErrorCode(HttpStatus status, String template) {
    this.status = status;
    this.template = template;
  }

  public HttpStatus status() {
    return status;
  }

  public String format(Object... args) {
    return args == null || args.length == 0 ? template : template.formatted(args);
  }
}
