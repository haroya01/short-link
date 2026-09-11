package com.example.short_link.link.stats.application.read;

import com.example.short_link.link.application.dto.LinkStats;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.stats.application.LinkInsights;
import java.time.Instant;
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
    Instant reportTime = Instant.now();
    String reportTz = LinkStatsDateSupport.offsetAt(reportZone, reportTime);

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
        insightsCalculator.computeReport(
            LinkInsights.ReportFacts.builder()
                .reportDate(reportTime.atZone(reportZone).toLocalDate())
                .total(totals.total())
                .human(totals.human())
                .bot(totals.bot())
                .heatmap(time.heatmap())
                .channels(channels.channels())
                .countries(geo.countries())
                .returnRate(returnRate)
                .lifecycle(lifecycle)
                .dailyClicks(time.daily())
                .clientApps(clientApps)
                .channelDepth(channelDepth)
                .build(),
            () -> lifecycleReader.channelFirstSeen(linkId));

    return LinkStats.builder()
        .shortCode(link.getShortCode())
        .timezone(reportZone.getId())
        .totalClicks(totals.total())
        .humanClicks(totals.human())
        .botClicks(totals.bot())
        .uniqueClicks(totals.unique())
        .previewClicks(totals.previewClicks())
        .profileClicks(totals.profileClicks())
        .firstClickAt(totals.firstClickAt())
        .lastClickAt(totals.lastClickAt())
        .timeToFirstClickMinutes(totals.timeToFirstClickMinutes())
        .peakHour(time.peakHour())
        .velocity(velocity)
        .returnRate(returnRate)
        .lifecycle(lifecycle)
        .dailyClicks(time.daily())
        .hourClicks(time.hourly())
        .dayOfWeekClicks(time.dayOfWeek())
        .heatmap(time.heatmap())
        .referrerClicks(channels.referrers())
        .referrerHostClicks(channels.referrerHosts())
        .channelClicks(channels.channels())
        .deviceClicks(devices.devices())
        .osClicks(devices.os())
        .browserClicks(devices.browsers())
        .botClicks2(devices.botBreakdown())
        .utmCampaignClicks(utm.campaigns())
        .utmSourceClicks(utm.sources())
        .utmMediumClicks(utm.mediums())
        .utmContentClicks(utm.contents())
        .utmTermClicks(utm.terms())
        .sourceChannelClicks(utm.sourceChannels())
        .clientAppClicks(clientApps)
        .fetchSiteClicks(fetchSites)
        .postClicks(postClicks)
        .channelDepth(channelDepth)
        .destinationClicks(destinations)
        .countryClicks(geo.countries())
        .regionClicks(geo.regions())
        .cityClicks(geo.cities())
        .languageClicks(geo.languages())
        .asnClicks(geo.asns())
        .datacenterClicks(geo.datacenterClicks())
        .insights(insights)
        .build();
  }
}
