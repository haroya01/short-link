package com.example.short_link.customdomain.application.helper;

import com.example.short_link.customdomain.application.dto.DomainSummary;
import com.example.short_link.customdomain.domain.CustomDomainEntity;
import java.time.Duration;
import java.time.Instant;

public final class CustomDomainPolicy {

  public static final int MAX_PER_USER = 5;

  /**
   * After registration, the auto-verify job polls DNS for this long. Beyond it, the user has to hit
   * the manual /verify endpoint themselves — covers cases where the DNS provider takes longer than
   * a usual TTL window to propagate.
   */
  public static final Duration AUTO_VERIFY_WINDOW = Duration.ofMinutes(10);

  public static final String TXT_PREFIX = "_kurl-verify.";

  private CustomDomainPolicy() {}

  public static String normalize(String input) {
    return input.trim().toLowerCase().replaceAll("^https?://", "").replaceAll("/.*$", "");
  }

  public static void validate(String domain) {
    if (domain.isBlank() || domain.length() > 253) {
      throw new IllegalArgumentException("invalid domain");
    }
    if (!domain.matches("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$")) {
      throw new IllegalArgumentException("invalid domain format");
    }
    if (domain.equals("kurl.me") || domain.endsWith(".kurl.me")) {
      throw new IllegalArgumentException("cannot register kurl.me itself");
    }
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
