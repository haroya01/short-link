package com.example.short_link.admin.domain.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface AdminAnalyticsRepository {

  List<CohortSizeRow> cohortSizes(Instant since);

  List<CohortActivityRow> cohortActivity(Instant since);

  List<LifecycleDayRow> lifecycleDays(int maxDay);

  List<ActivePerDayRow> dailyActiveUsers(Instant since, String timezone);

  List<ActivePerBucketRow> weeklyActiveUsers(Instant since);

  List<ActivePerBucketStringRow> monthlyActiveUsers(Instant since);

  interface CohortSizeRow {
    Integer getCohortKey();

    Long getCount();
  }

  interface CohortActivityRow {
    Integer getCohortKey();

    Integer getActivityKey();

    Long getActiveUsers();
  }

  interface LifecycleDayRow {
    Integer getDay();

    Long getClicks();

    Long getLinks();
  }

  interface ActivePerDayRow {
    LocalDate getBucket();

    Long getActive();
  }

  interface ActivePerBucketRow {
    Integer getBucket();

    Long getActive();
  }

  interface ActivePerBucketStringRow {
    String getBucket();

    Long getActive();
  }
}
