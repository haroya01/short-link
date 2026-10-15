package com.example.short_link.federation.domain;

public record RemoteAccountStanding(boolean domainBlocked, boolean blocking, boolean muting) {

  public static final RemoteAccountStanding NONE = new RemoteAccountStanding(false, false, false);

  public RemoteAccountStanding withBlocking(boolean blocking) {
    return new RemoteAccountStanding(domainBlocked, blocking, muting);
  }

  public RemoteAccountStanding withMuting(boolean muting) {
    return new RemoteAccountStanding(domainBlocked, blocking, muting);
  }
}
