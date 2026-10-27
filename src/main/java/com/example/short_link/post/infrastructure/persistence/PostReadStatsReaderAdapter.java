package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.common.analytics.VisitProjections;
import com.example.short_link.post.domain.repository.PostReadStatsReader;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class PostReadStatsReaderAdapter implements PostReadStatsReader {

  private final JpaPostReadStatsRepository jpa;

  @Override
  public long countViews(Collection<Long> postIds) {
    return jpa.countViews(postIds);
  }

  @Override
  public long countHuman(Collection<Long> postIds) {
    return jpa.countHuman(postIds);
  }

  @Override
  public long countBot(Collection<Long> postIds) {
    return jpa.countBot(postIds);
  }

  @Override
  public long countUnique(Collection<Long> postIds) {
    return jpa.countUnique(postIds);
  }

  @Override
  public Instant firstViewedAt(Collection<Long> postIds) {
    return jpa.firstViewedAt(postIds);
  }

  @Override
  public Instant lastViewedAt(Collection<Long> postIds) {
    return jpa.lastViewedAt(postIds);
  }

  @Override
  public List<VisitProjections.DailyVisitRow> daily(
      Collection<Long> postIds, Instant from, String tz) {
    return jpa.daily(postIds, from, tz);
  }

  @Override
  public List<VisitProjections.HourVisitRow> hourly(Collection<Long> postIds, String tz) {
    return jpa.hourly(postIds, tz);
  }

  @Override
  public List<VisitProjections.HeatmapVisitRow> heatmap(Collection<Long> postIds, String tz) {
    return jpa.heatmap(postIds, tz);
  }

  @Override
  public List<VisitProjections.CountryVisitRow> topCountries(Collection<Long> postIds, int limit) {
    return jpa.topCountries(postIds, PageRequest.ofSize(limit));
  }

  @Override
  public List<VisitProjections.DeviceVisitRow> topDevices(Collection<Long> postIds, int limit) {
    return jpa.topDevices(postIds, PageRequest.ofSize(limit));
  }

  @Override
  public List<VisitProjections.BrowserVisitRow> topBrowsers(Collection<Long> postIds, int limit) {
    return jpa.topBrowsers(postIds, PageRequest.ofSize(limit));
  }

  @Override
  public List<VisitProjections.ReferrerHostVisitRow> topReferrerHosts(
      Collection<Long> postIds, int limit) {
    return jpa.topReferrerHosts(postIds, PageRequest.ofSize(limit));
  }

  @Override
  public List<VisitProjections.SourceChannelVisitRow> topSourceChannels(
      Collection<Long> postIds, int limit) {
    return jpa.topSourceChannels(postIds, PageRequest.ofSize(limit));
  }

  @Override
  public List<VisitProjections.UtmCampaignVisitRow> topUtmCampaigns(
      Collection<Long> postIds, int limit) {
    return jpa.topUtmCampaigns(postIds, PageRequest.ofSize(limit));
  }

  @Override
  public List<VisitProjections.UtmSourceVisitRow> topUtmSources(
      Collection<Long> postIds, int limit) {
    return jpa.topUtmSources(postIds, PageRequest.ofSize(limit));
  }
}
