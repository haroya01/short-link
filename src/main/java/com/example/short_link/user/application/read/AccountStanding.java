package com.example.short_link.user.application.read;

import java.time.Instant;

public record AccountStanding(Instant joinedAt, Status status, Instant suspendedUntil) {

  public enum Status {
    ACTIVE,
    SUSPENDED,
    BANNED,
    DELETED
  }
}
