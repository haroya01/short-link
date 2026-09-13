package com.example.short_link.post.domain.repository;

import com.example.short_link.link.stats.domain.repository.projection.ClickProjections;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Aggregates post_view_event for a non-empty set of post IDs; callers must short-circuit empty
 * sets. Visitor breakdowns use human reads; total and bot counts are separate.
 */
public interface PostReadStatsReader {

  long countViews(Collection<Long> postIds);

  long countHuman(Collection<Long> postIds);

  long countBot(Collection<Long> postIds);

  long countUnique(Collection<Long> postIds);

  Instant firstViewedAt(Collection<Long> postIds);

  Instant lastViewedAt(Collection<Long> postIds);

  List<ClickProjections.DailyClickRow> daily(Collection<Long> postIds, Instant from, String tz);

  List<ClickProjections.HourClickRow> hourly(Collection<Long> postIds, String tz);

  List<ClickProjections.HeatmapRow> heatmap(Collection<Long> postIds, String tz);

  List<ClickProjections.CountryClickRow> topCountries(Collection<Long> postIds, int limit);

  List<ClickProjections.DeviceClickRow> topDevices(Collection<Long> postIds, int limit);

  List<ClickProjections.BrowserClickRow> topBrowsers(Collection<Long> postIds, int limit);

  List<ClickProjections.ReferrerHostClickRow> topReferrerHosts(Collection<Long> postIds, int limit);

  List<ClickProjections.SourceChannelClickRow> topSourceChannels(
      Collection<Long> postIds, int limit);

  List<ClickProjections.UtmCampaignClickRow> topUtmCampaigns(Collection<Long> postIds, int limit);

  List<ClickProjections.UtmSourceClickRow> topUtmSources(Collection<Long> postIds, int limit);
}
