package com.example.short_link.profile.infrastructure.persistence;

import com.example.short_link.common.analytics.VisitProjections;
import com.example.short_link.profile.domain.repository.ProfileVisitRepository;
import com.example.short_link.profile.domain.visit.ProfileVisitEntity;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
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
  public List<VisitProjections.DailyVisitRow> findDailyVisits(
      Long uid, Instant from, Instant until, String tz) {
    return jpa.findDailyVisits(uid, from, until, tz);
  }

  @Override
  public List<VisitProjections.HourVisitRow> findHourlyVisits(
      Long uid, Instant from, Instant until, String tz) {
    return jpa.findHourlyVisits(uid, from, until, tz);
  }

  @Override
  public List<VisitProjections.HeatmapVisitRow> findHeatmap(
      Long uid, Instant from, Instant until, String tz) {
    return jpa.findHeatmap(uid, from, until, tz);
  }

  @Override
  public List<VisitProjections.CountryVisitRow> topCountries(Long uid, int limit) {
    return jpa.findCountryVisits(uid, PageRequest.ofSize(limit));
  }

  @Override
  public List<VisitProjections.DeviceVisitRow> findDeviceVisits(Long uid) {
    return jpa.findDeviceVisits(uid);
  }

  @Override
  public List<VisitProjections.BrowserVisitRow> topBrowsers(Long uid, int limit) {
    return jpa.findBrowserVisits(uid, PageRequest.ofSize(limit));
  }

  @Override
  public List<VisitProjections.ReferrerHostVisitRow> topReferrerHosts(Long uid, int limit) {
    return jpa.findReferrerHostVisits(uid, PageRequest.ofSize(limit));
  }

  @Override
  public List<VisitProjections.SourceChannelVisitRow> topSourceChannels(Long uid, int limit) {
    return jpa.findSourceChannelVisits(uid, PageRequest.ofSize(limit));
  }

  @Override
  public List<VisitProjections.UtmCampaignVisitRow> topUtmCampaigns(Long uid, int limit) {
    return jpa.findUtmCampaignVisits(uid, PageRequest.ofSize(limit));
  }

  @Override
  public List<VisitProjections.UtmSourceVisitRow> topUtmSources(Long uid, int limit) {
    return jpa.findUtmSourceVisits(uid, PageRequest.ofSize(limit));
  }
}
