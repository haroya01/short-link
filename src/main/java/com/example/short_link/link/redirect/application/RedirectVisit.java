package com.example.short_link.link.redirect.application;

public record RedirectVisit(
    String referrer,
    String userAgent,
    String clientIp,
    String acceptLanguage,
    String sourceChannel,
    Long postId,
    boolean gpc,
    String fetchSite,
    Visitor visitor) {

  public enum Visitor {
    PERSON,
    PREFETCH,
    OPERATOR
  }

  public boolean prefetch() {
    return visitor == Visitor.PREFETCH;
  }

  public boolean operator() {
    return visitor == Visitor.OPERATOR;
  }
}
