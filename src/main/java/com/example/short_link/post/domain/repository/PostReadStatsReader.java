package com.example.short_link.post.domain.repository;

import com.example.short_link.link.stats.domain.repository.projection.ClickProjections;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Aggregates post_view_event rows over a set of post ids (one post, or a series' member posts) into
 * the visitor-dimension breakdowns the reader dashboard shows. Mirrors {@code
 * ProfileVisitBreakdownReader} + the profile time-series queries, scoped by post_id instead of
 * profile_user_id. Human-only (is_bot = false) except the explicit bot count. Callers must pass a
 * non-empty id set (the service short-circuits empty → empty stats, since SQL {@code IN ()} is
 * invalid).
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
