package com.example.short_link.user.application.read;

import java.time.Instant;

public record MuteStatus(boolean muted, boolean notifications, Instant expiresAt) {

  public static final MuteStatus NONE = new MuteStatus(false, false, null);
}
