package com.example.short_link.profile.domain.repository;

import com.example.short_link.common.analytics.VisitProjections;
import com.example.short_link.profile.domain.visit.ProfileVisitEntity;
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

  List<VisitProjections.DailyVisitRow> findDailyVisits(
      Long uid, Instant from, Instant until, String tz);

  List<VisitProjections.HourVisitRow> findHourlyVisits(
      Long uid, Instant from, Instant until, String tz);

  List<VisitProjections.HeatmapVisitRow> findHeatmap(
      Long uid, Instant from, Instant until, String tz);

  List<VisitProjections.CountryVisitRow> topCountries(Long uid, int limit);

  List<VisitProjections.DeviceVisitRow> findDeviceVisits(Long uid);

  List<VisitProjections.BrowserVisitRow> topBrowsers(Long uid, int limit);

  List<VisitProjections.ReferrerHostVisitRow> topReferrerHosts(Long uid, int limit);

  List<VisitProjections.SourceChannelVisitRow> topSourceChannels(Long uid, int limit);

  List<VisitProjections.UtmCampaignVisitRow> topUtmCampaigns(Long uid, int limit);

  List<VisitProjections.UtmSourceVisitRow> topUtmSources(Long uid, int limit);
}
