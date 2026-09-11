package com.example.short_link.link.safety.application;

/** A lookup failure whose policy does not depend on an HTTP client or a threat provider. */
public final class UrlThreatLookupException extends RuntimeException {

  public enum Kind {
    AUTHENTICATION,
    UNAVAILABLE
  }

  private final Kind kind;

  private UrlThreatLookupException(Kind kind, Throwable cause) {
    super("URL threat lookup failed: " + kind, cause);
    this.kind = kind;
  }

  public static UrlThreatLookupException authenticationFailure(Throwable cause) {
    return new UrlThreatLookupException(Kind.AUTHENTICATION, cause);
  }

  public static UrlThreatLookupException unavailable(Throwable cause) {
    return new UrlThreatLookupException(Kind.UNAVAILABLE, cause);
  }

  public Kind kind() {
    return kind;
  }
}
