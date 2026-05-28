package com.example.short_link.profile.infrastructure.persistence;

import com.example.short_link.link.stats.domain.repository.projection.ClickProjections;
import com.example.short_link.profile.domain.visit.ProfileVisitEntity;
import com.example.short_link.profile.domain.visit.ProfileVisitRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class ProfileVisitRepositoryAdapter implements ProfileVisitRepository {

  private final JpaProfileVisitRepository jpa;

  @Override
  public ProfileVisitEntity save(ProfileVisitEntity visit) {
    return jpa.save(visit);
  }

  @Override
  public long countByProfileUserId(Long profileUserId) {
    return jpa.countByProfileUserId(profileUserId);
  }

  @Override
  public long countByProfileUserIdAndBotFalse(Long profileUserId) {
    return jpa.countByProfileUserIdAndBotFalse(profileUserId);
  }

  @Override
  public long countByProfileUserIdAndBotFalseAndVisitedAtAfter(Long profileUserId, Instant after) {
    return jpa.countByProfileUserIdAndBotFalseAndVisitedAtAfter(profileUserId, after);
  }

  @Override
  public long countBotByProfileUserId(Long uid) {
    return jpa.countBotByProfileUserId(uid);
  }

  @Override
  public long countUniqueVisitorsByProfileUserId(Long uid) {
    return jpa.countUniqueVisitorsByProfileUserId(uid);
  }

  @Override
  public Instant findFirstVisitAt(Long uid) {
    return jpa.findFirstVisitAt(uid);
  }

  @Override
  public Instant findLastVisitAt(Long uid) {
    return jpa.findLastVisitAt(uid);
  }

  @Override
  public List<ClickProjections.DailyClickRow> findDailyVisits(Long uid, Instant from, String tz) {
    return jpa.findDailyVisits(uid, from, tz);
  }

  @Override
  public List<ClickProjections.HourClickRow> findHourlyVisits(Long uid, String tz) {
    return jpa.findHourlyVisits(uid, tz);
  }

  @Override
  public List<ClickProjections.HeatmapRow> findHeatmap(Long uid, String tz) {
    return jpa.findHeatmap(uid, tz);
  }

  @Override
  public List<ClickProjections.DeviceClickRow> findDeviceVisits(Long uid) {
    return jpa.findDeviceVisits(uid);
  }
}
