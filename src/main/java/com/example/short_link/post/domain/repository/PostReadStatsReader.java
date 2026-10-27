package com.example.short_link.post.domain.repository;

import com.example.short_link.common.analytics.VisitProjections;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

// Aggregates post_view_event for a non-empty set of post IDs; callers must short-circuit empty
// sets. Visitor breakdowns use human reads; total and bot counts are separate.
public interface PostReadStatsReader {

  long countViews(Collection<Long> postIds);

  long countHuman(Collection<Long> postIds);

  long countBot(Collection<Long> postIds);

  long countUnique(Collection<Long> postIds);

  Instant firstViewedAt(Collection<Long> postIds);

  Instant lastViewedAt(Collection<Long> postIds);

  List<VisitProjections.DailyVisitRow> daily(
      Collection<Long> postIds, Instant from, Instant until, String tz);

  List<VisitProjections.HourVisitRow> hourly(
      Collection<Long> postIds, Instant from, Instant until, String tz);

  List<VisitProjections.HeatmapVisitRow> heatmap(
      Collection<Long> postIds, Instant from, Instant until, String tz);

  List<VisitProjections.CountryVisitRow> topCountries(Collection<Long> postIds, int limit);

  List<VisitProjections.DeviceVisitRow> topDevices(Collection<Long> postIds, int limit);

  List<VisitProjections.BrowserVisitRow> topBrowsers(Collection<Long> postIds, int limit);

  List<VisitProjections.ReferrerHostVisitRow> topReferrerHosts(Collection<Long> postIds, int limit);

  List<VisitProjections.SourceChannelVisitRow> topSourceChannels(
      Collection<Long> postIds, int limit);

  List<VisitProjections.UtmCampaignVisitRow> topUtmCampaigns(Collection<Long> postIds, int limit);

  List<VisitProjections.UtmSourceVisitRow> topUtmSources(Collection<Long> postIds, int limit);
}
