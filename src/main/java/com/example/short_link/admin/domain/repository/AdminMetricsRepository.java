package com.example.short_link.admin.domain.repository;

import com.example.short_link.link.domain.ShortCode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface AdminMetricsRepository {

  long totalClicks();

  long clicksSince(Instant since);

  long linksWithoutClicks();

  List<DailyRow> dailySignupsSince(Instant since, String tz);

  List<DailyRow> dailyLinksSince(Instant since, String tz);

  List<DailyRow> dailyClicksSince(Instant since, String tz);

  List<LinkMetricRow> linkMetricRowsByShortCodes(Collection<ShortCode> shortCodes);

  record StatPage<T>(List<T> items, long total) {}

  interface DailyRow {
    LocalDate getDay();

    Long getCount();
  }

  interface UserStatRow {
    Long getUserId();

    String getEmail();

    Long getCount();
  }

  interface LinkStatRow {
    String getShortCode();

    Long getCount();

    String getOwnerEmail();
  }

  interface LinkMetricRow {
    String getShortCode();

    String getOriginalUrl();

    Long getUserId();

    String getOwnerEmail();

    Long getTotalRedirects();

    Instant getLastRedirectAt();
  }
}
