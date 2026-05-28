package com.example.short_link.admin.infrastructure.persistence;

import com.example.short_link.admin.domain.repository.AdminAnalyticsRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class AdminAnalyticsRepositoryAdapter implements AdminAnalyticsRepository {

  private final JpaAdminAnalyticsRepository jpa;

  @Override
  public List<CohortSizeRow> cohortSizes(Instant since) {
    return jpa.cohortSizes(since);
  }

  @Override
  public List<CohortActivityRow> cohortActivity(Instant since) {
    return jpa.cohortActivity(since);
  }

  @Override
  public List<LifecycleDayRow> lifecycleDays(int maxDay) {
    return jpa.lifecycleDays(maxDay);
  }

  @Override
  public List<ActivePerDayRow> dailyActiveUsers(Instant since, String timezone) {
    return jpa.dailyActiveUsers(since, timezone);
  }

  @Override
  public List<ActivePerBucketRow> weeklyActiveUsers(Instant since) {
    return jpa.weeklyActiveUsers(since);
  }

  @Override
  public List<ActivePerBucketStringRow> monthlyActiveUsers(Instant since) {
    return jpa.monthlyActiveUsers(since);
  }
}
