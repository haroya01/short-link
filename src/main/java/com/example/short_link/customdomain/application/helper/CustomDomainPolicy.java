package com.example.short_link.customdomain.application.helper;

import com.example.short_link.customdomain.application.dto.DomainSummary;
import com.example.short_link.customdomain.domain.CustomDomainEntity;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

public final class CustomDomainPolicy {

  public static final int MAX_PER_USER = 5;

  public static final Duration AUTO_VERIFY_WINDOW = Duration.ofMinutes(10);

  public static final String TXT_PREFIX = "_kurl-verify.";

  private CustomDomainPolicy() {}

  public static String normalize(String input) {
    if (input == null) throw new IllegalArgumentException("invalid domain");
    return input
        .trim()
        .toLowerCase(Locale.ROOT)
        .replaceAll("^https?://", "")
        .replaceAll("/.*$", "");
  }

  public static DomainSummary toSummary(CustomDomainEntity e) {
    Instant autoUntil = e.isVerified() ? null : e.getCreatedAt().plus(AUTO_VERIFY_WINDOW);
    return new DomainSummary(
        e.getId(),
        e.getDomain(),
        e.getVerificationToken(),
        TXT_PREFIX + e.getDomain(),
        e.isVerified(),
        e.getVerifiedAt(),
        e.getLastCheckedAt(),
        e.getCreatedAt(),
        autoUntil);
  }
}
