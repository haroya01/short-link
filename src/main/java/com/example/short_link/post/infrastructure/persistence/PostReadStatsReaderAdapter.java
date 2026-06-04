package com.example.short_link.post.infrastructure.persistence;

import com.example.short_link.link.stats.domain.repository.projection.ClickProjections;
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
  public List<ClickProjections.DailyClickRow> daily(
      Collection<Long> postIds, Instant from, String tz) {
    return jpa.daily(postIds, from, tz);
  }

  @Override
  public List<ClickProjections.HourClickRow> hourly(Collection<Long> postIds, String tz) {
    return jpa.hourly(postIds, tz);
  }

  @Override
  public List<ClickProjections.HeatmapRow> heatmap(Collection<Long> postIds, String tz) {
    return jpa.heatmap(postIds, tz);
  }

  @Override
  public List<ClickProjections.CountryClickRow> topCountries(Collection<Long> postIds, int limit) {
    return jpa.topCountries(postIds, PageRequest.ofSize(limit));
  }

  @Override
  public List<ClickProjections.DeviceClickRow> topDevices(Collection<Long> postIds, int limit) {
    return jpa.topDevices(postIds, PageRequest.ofSize(limit));
  }

  @Override
  public List<ClickProjections.BrowserClickRow> topBrowsers(Collection<Long> postIds, int limit) {
    return jpa.topBrowsers(postIds, PageRequest.ofSize(limit));
  }

  @Override
  public List<ClickProjections.ReferrerHostClickRow> topReferrerHosts(
      Collection<Long> postIds, int limit) {
    return jpa.topReferrerHosts(postIds, PageRequest.ofSize(limit));
  }

  @Override
  public List<ClickProjections.SourceChannelClickRow> topSourceChannels(
      Collection<Long> postIds, int limit) {
    return jpa.topSourceChannels(postIds, PageRequest.ofSize(limit));
  }

  @Override
  public List<ClickProjections.UtmCampaignClickRow> topUtmCampaigns(
      Collection<Long> postIds, int limit) {
    return jpa.topUtmCampaigns(postIds, PageRequest.ofSize(limit));
  }

  @Override
  public List<ClickProjections.UtmSourceClickRow> topUtmSources(
      Collection<Long> postIds, int limit) {
    return jpa.topUtmSources(postIds, PageRequest.ofSize(limit));
  }
}
