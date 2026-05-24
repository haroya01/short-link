package com.example.short_link.admin.application;

import com.example.short_link.admin.exception.InvalidActivePeriodException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminAnalyticsService {

  private static final String REPORT_TZ = "+09:00";

  private final AdminAnalyticsRepository repo;

  @Cacheable(value = "admin-overview", key = "'cohort:' + #weeks")
  @Transactional(readOnly = true)
  public AdminCohort cohort(int weeks) {
    int w = clamp(weeks, 1, 26);
    Instant since = Instant.now().minus(Duration.ofDays(w * 7L));

    Map<Integer, Long> sizes = new LinkedHashMap<>();
    repo.cohortSizes(since).forEach(r -> sizes.put(r.getCohortKey(), r.getCount()));

    Map<Integer, Map<Integer, Long>> activity = new LinkedHashMap<>();
    for (var row : repo.cohortActivity(since)) {
      activity
          .computeIfAbsent(row.getCohortKey(), k -> new TreeMap<>())
          .put(row.getActivityKey(), row.getActiveUsers());
    }

    List<AdminCohort.Row> rows = new ArrayList<>();
    for (var entry : sizes.entrySet()) {
      int cohortKey = entry.getKey();
      long size = entry.getValue();
      Map<Integer, Long> perWeek = activity.getOrDefault(cohortKey, Map.of());
      List<AdminCohort.Cell> cells = new ArrayList<>();
      for (int offset = 0; offset < w; offset++) {
        int activityKey = addWeeks(cohortKey, offset);
        long active = perWeek.getOrDefault(activityKey, 0L);
        double ratio = size == 0 ? 0.0 : (double) active / size;
        cells.add(new AdminCohort.Cell(offset, active, round3(ratio)));
      }
      rows.add(new AdminCohort.Row(formatYearWeek(cohortKey), size, cells));
    }
    return new AdminCohort(w, rows);
  }

  @Cacheable(value = "admin-overview", key = "'lifecycle:' + #maxDay")
  @Transactional(readOnly = true)
  public AdminLifecycle lifecycle(int maxDay) {
    int max = clamp(maxDay, 1, 90);
    List<AdminLifecycle.DayPoint> days =
        repo.lifecycleDays(max).stream()
            .map(r -> new AdminLifecycle.DayPoint(r.getDay(), r.getClicks(), r.getLinks()))
            .toList();
    return new AdminLifecycle(max, days);
  }

  @Cacheable(value = "admin-overview", key = "'active:' + #period")
  @Transactional(readOnly = true)
  public AdminActiveUsers activeUsers(String period) {
    String p = period == null ? "day" : period.toLowerCase();
    return switch (p) {
      case "day", "dau" -> dailyActive();
      case "week", "wau" -> weeklyActive();
      case "month", "mau" -> monthlyActive();
      default -> throw new InvalidActivePeriodException(period);
    };
  }

  private AdminActiveUsers dailyActive() {
    Instant since = Instant.now().minus(Duration.ofDays(30));
    List<AdminActiveUsers.Bucket> buckets =
        repo.dailyActiveUsers(since, REPORT_TZ).stream()
            .map(r -> new AdminActiveUsers.Bucket(r.getBucket().toString(), r.getActive()))
            .toList();
    return new AdminActiveUsers("day", buckets);
  }

  private AdminActiveUsers weeklyActive() {
    Instant since = Instant.now().minus(Duration.ofDays(12 * 7));
    List<AdminActiveUsers.Bucket> buckets =
        repo.weeklyActiveUsers(since).stream()
            .map(r -> new AdminActiveUsers.Bucket(formatYearWeek(r.getBucket()), r.getActive()))
            .toList();
    return new AdminActiveUsers("week", buckets);
  }

  private AdminActiveUsers monthlyActive() {
    Instant since = Instant.now().minus(Duration.ofDays(366));
    List<AdminActiveUsers.Bucket> buckets =
        repo.monthlyActiveUsers(since).stream()
            .map(r -> new AdminActiveUsers.Bucket(r.getBucket(), r.getActive()))
            .toList();
    return new AdminActiveUsers("month", buckets);
  }

  static int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }

  static double round3(double v) {
    return Math.round(v * 1000.0) / 1000.0;
  }

  static int addWeeks(int yearweek, int weeks) {
    int year = yearweek / 100;
    int week = yearweek % 100;
    LocalDate base = LocalDate.now().withYear(year);
    base =
        base.with(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear(), week)
            .with(java.time.temporal.WeekFields.ISO.dayOfWeek(), 1)
            .plusWeeks(weeks);
    int newYear = base.get(java.time.temporal.WeekFields.ISO.weekBasedYear());
    int newWeek = base.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear());
    return newYear * 100 + newWeek;
  }

  static String formatYearWeek(int yearweek) {
    int year = yearweek / 100;
    int week = yearweek % 100;
    return String.format("%04d-W%02d", year, week);
  }
}
