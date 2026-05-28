package com.example.short_link.profile.domain.visit;

import com.example.short_link.link.stats.domain.repository.projection.ClickProjections;
import java.time.Instant;
import java.util.List;

public interface ProfileVisitRepository {

  ProfileVisitEntity save(ProfileVisitEntity visit);

  long countByProfileUserId(Long profileUserId);

  long countByProfileUserIdAndBotFalse(Long profileUserId);

  long countByProfileUserIdAndBotFalseAndVisitedAtAfter(Long profileUserId, Instant after);

  long countBotByProfileUserId(Long uid);

  long countUniqueVisitorsByProfileUserId(Long uid);

  Instant findFirstVisitAt(Long uid);

  Instant findLastVisitAt(Long uid);

  List<ClickProjections.DailyClickRow> findDailyVisits(Long uid, Instant from, String tz);

  List<ClickProjections.HourClickRow> findHourlyVisits(Long uid, String tz);

  List<ClickProjections.HeatmapRow> findHeatmap(Long uid, String tz);

  List<ClickProjections.DeviceClickRow> findDeviceVisits(Long uid);
}
