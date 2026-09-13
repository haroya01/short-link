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

  List<RecentLinkRow> recentLinks(int limit);

  /** 클릭 기록에는 IP와 방문자 해시를 포함하지 않는다. */
  List<RecentClickRow> recentClicks(int limit);

  /** 봇 클릭을 제외한 횟수로 정렬한다. */
  List<LinkStatRow> topLinksByClicksSince(Instant since, int limit);

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

  interface RecentLinkRow {
    String getShortCode();

    String getOriginalUrl();

    String getOwnerEmail();

    Instant getCreatedAt();
  }

  interface RecentClickRow {
    String getShortCode();

    Instant getClickedAt();

    String getCountryCode();

    String getReferrerHost();

    String getDeviceClass();
  }
}
