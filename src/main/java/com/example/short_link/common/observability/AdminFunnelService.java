package com.example.short_link.common.observability;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Counts how many users registered within the requested window have reached each of the four
 * pipeline milestones — signed up → created their first link → received a first click on any link →
 * wired a webhook on any link. Conversion ratios on top let the operator see at a glance where the
 * funnel narrows (e.g. lots of accounts but few people creating links → onboarding problem; many
 * links but no clicks → distribution problem).
 *
 * <p>All counts are point-in-time: a user who signed up two days ago and creates their first
 * webhook today is counted in the {@code withWebhook} bucket for the 7-day window. Conversions are
 * derived in-process, not pinned to the user's signup-time progress.
 */
@Service
public class AdminFunnelService {

  @PersistenceContext private EntityManager em;

  private final Clock clock;

  @Autowired
  public AdminFunnelService() {
    this(Clock.systemUTC());
  }

  AdminFunnelService(Clock clock) {
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public Funnel snapshot(Window window) {
    Instant now = clock.instant();
    Instant since = window.duration() == null ? Instant.EPOCH : now.minus(window.duration());

    long users =
        ((Number)
                em.createQuery("SELECT COUNT(u) FROM UserEntity u WHERE u.createdAt >= :since")
                    .setParameter("since", since)
                    .getSingleResult())
            .longValue();

    long withLink =
        ((Number)
                em.createQuery(
                        "SELECT COUNT(DISTINCT l.userId) FROM LinkEntity l "
                            + "WHERE l.userId IN (SELECT u.id FROM UserEntity u WHERE u.createdAt >= :since)")
                    .setParameter("since", since)
                    .getSingleResult())
            .longValue();

    long withClick =
        ((Number)
                em.createQuery(
                        "SELECT COUNT(DISTINCT l.userId) FROM LinkEntity l "
                            + "WHERE l.userId IN (SELECT u.id FROM UserEntity u WHERE u.createdAt >= :since) "
                            + "AND EXISTS (SELECT 1 FROM ClickEventEntity c WHERE c.linkId = l.id AND c.bot = false)")
                    .setParameter("since", since)
                    .getSingleResult())
            .longValue();

    long withWebhook =
        ((Number)
                em.createQuery(
                        "SELECT COUNT(DISTINCT l.userId) FROM LinkEntity l "
                            + "WHERE l.userId IN (SELECT u.id FROM UserEntity u WHERE u.createdAt >= :since) "
                            + "AND EXISTS (SELECT 1 FROM LinkWebhookEntity w WHERE w.linkId = l.id)")
                    .setParameter("since", since)
                    .getSingleResult())
            .longValue();

    return new Funnel(
        window,
        users,
        withLink,
        withClick,
        withWebhook,
        conversions(users, withLink, withClick, withWebhook));
  }

  private Map<String, Double> conversions(
      long users, long withLink, long withClick, long withWebhook) {
    Map<String, Double> c = new LinkedHashMap<>();
    c.put("signupToLink", ratio(withLink, users));
    c.put("linkToClick", ratio(withClick, withLink));
    c.put("clickToWebhook", ratio(withWebhook, withClick));
    return c;
  }

  private static double ratio(long numerator, long denominator) {
    return denominator == 0 ? 0.0 : (double) numerator / (double) denominator;
  }

  public record Funnel(
      Window window,
      long users,
      long withLink,
      long withClick,
      long withWebhook,
      Map<String, Double> conversion) {}

  public enum Window {
    D1(Duration.ofDays(1)),
    D7(Duration.ofDays(7)),
    D30(Duration.ofDays(30)),
    ALL(null);

    private final Duration duration;

    Window(Duration duration) {
      this.duration = duration;
    }

    public Duration duration() {
      return duration;
    }

    public static Window parse(String raw) {
      if (raw == null) return D7;
      String s = raw.trim().toLowerCase(Locale.ROOT);
      return switch (s) {
        case "1d", "d1", "day" -> D1;
        case "7d", "d7", "week" -> D7;
        case "30d", "d30", "month" -> D30;
        case "all" -> ALL;
        default -> D7;
      };
    }
  }
}
