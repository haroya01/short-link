package com.example.short_link.link.stats.application.read;

import com.example.short_link.link.application.dto.LinkStats;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.stats.application.LinkInsights;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class LinkStatsReportAssembler {

  private final LinkStatsTotalsReader totalsReader;
  private final LinkStatsTimeBucketsReader timeReader;
  private final LinkStatsDimensionBreakdownsReader dimensionsReader;
  private final LinkStatsLifecycleReader lifecycleReader;
  private final LinkInsights insightsCalculator;

  LinkStats assemble(LinkEntity link, ZoneId reportZone) {
    LinkId linkId = link.linkId();
    String reportTz = LinkStatsDateSupport.currentOffset(reportZone);

    LinkStatsTotalsReader.Totals totals = totalsReader.totals(linkId, link.getCreatedAt());
    LinkStats.Velocity velocity = totalsReader.velocity(linkId);
    LinkStatsTimeBucketsReader.TimeBuckets time = timeReader.read(linkId, reportTz);
    LinkStatsDimensionBreakdownsReader.ChannelBreakdowns channels =
        dimensionsReader.channels(linkId);
    LinkStatsDimensionBreakdownsReader.DeviceBreakdowns devices = dimensionsReader.devices(linkId);
    LinkStatsDimensionBreakdownsReader.UtmBreakdowns utm = dimensionsReader.utm(linkId);
    LinkStatsDimensionBreakdownsReader.GeoBreakdowns geo = dimensionsReader.geo(linkId);
    List<LinkStats.DestinationClick> destinations = dimensionsReader.destinations(link);
    List<LinkStats.ClientAppClick> clientApps = dimensionsReader.clientApps(linkId);
    List<LinkStats.FetchSiteClick> fetchSites = dimensionsReader.fetchSites(linkId);
    List<LinkStats.PostClick> postClicks = dimensionsReader.postClicks(linkId);
    LinkStats.ReturnRate returnRate = lifecycleReader.returnRate(linkId);
    LinkStats.Lifecycle lifecycle = lifecycleReader.lifecycle(linkId);
    List<LinkStats.ChannelDepth> channelDepth = lifecycleReader.channelDepth(linkId);

    List<LinkStats.Insight> insights =
        new java.util.ArrayList<>(
            insightsCalculator.compute(
                totals.total(),
                totals.bot(),
                time.heatmap(),
                channels.channels(),
                geo.countries(),
                returnRate,
                lifecycle,
                time.daily()));
    // 채널 점프는 시계열 first-seen 쿼리가 필요해 compute() 밖에서 곁들인다(원래 청중 탈출 신호).
    if (totals.total() >= 10) {
      lifecycleReader.channelJump(linkId).ifPresent(insights::add);
    }
    // 인앱 비중·채널 충성도도 같은 자리에서 — 이미 읽어 둔 브레이크다운으로 만드는 규칙이라 추가 쿼리가 없다.
    insightsCalculator.inAppBrowser(clientApps, totals.human()).ifPresent(insights::add);
    insightsCalculator.channelLoyalty(channelDepth).ifPresent(insights::add);

    return new LinkStats(
        link.getShortCode(),
        reportZone.getId(),
        totals.total(),
        totals.human(),
        totals.bot(),
        totals.unique(),
        totals.previewClicks(),
        totals.profileClicks(),
        totals.firstClickAt(),
        totals.lastClickAt(),
        totals.timeToFirstClickMinutes(),
        time.peakHour(),
        velocity,
        returnRate,
        lifecycle,
        time.daily(),
        time.hourly(),
        time.dayOfWeek(),
        time.heatmap(),
        channels.referrers(),
        channels.referrerHosts(),
        channels.channels(),
        devices.devices(),
        devices.os(),
        devices.browsers(),
        devices.botBreakdown(),
        utm.campaigns(),
        utm.sources(),
        utm.mediums(),
        utm.contents(),
        utm.terms(),
        utm.sourceChannels(),
        clientApps,
        fetchSites,
        postClicks,
        channelDepth,
        destinations,
        geo.countries(),
        geo.regions(),
        geo.cities(),
        geo.languages(),
        geo.asns(),
        geo.datacenterClicks(),
        insights);
  }
}
