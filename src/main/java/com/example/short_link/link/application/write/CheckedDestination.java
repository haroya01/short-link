package com.example.short_link.link.application.write;

public final class CheckedDestination {

  private final String url;
  private final boolean allowSelfHost;

  CheckedDestination(String url, boolean allowSelfHost) {
    this.url = url;
    this.allowSelfHost = allowSelfHost;
  }

  boolean isFor(CreateLinkCommand command) {
    return url.equals(command.url()) && allowSelfHost == command.allowSelfHost();
  }
}
