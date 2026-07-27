package com.example.short_link.link.stats.application.read;

import com.example.short_link.common.post.PostTitleReader;
import com.example.short_link.link.application.dto.LinkStats;
import com.example.short_link.link.classifier.application.ReferrerChannelClassifier;
import com.example.short_link.link.destination.domain.LinkDestinationEntity;
import com.example.short_link.link.destination.domain.repository.LinkDestinationRepository;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.stats.domain.repository.ClickTotalsReadRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class LinkStatsDimensionBreakdownsReader {

  private static final int TOP_SIZE = 50;

  private final ClickDimensionReader clickDimensions;
  private final ClickTotalsReadRepository clickTotals;
  private final ReferrerChannelClassifier channelClassifier;
  private final LinkDestinationRepository destinationRepository;
  private final PostTitleReader postTitles;

  ChannelBreakdowns channels(LinkId linkId) {
    List<LinkStats.ReferrerClick> referrers =
        clickDimensions.findTopReferrerClicks(linkId.value(), TOP_SIZE).stream()
            .map(r -> new LinkStats.ReferrerClick(r.getReferrer(), r.getCount()))
            .toList();
    List<LinkStats.ReferrerHostClick> referrerHosts =
        clickDimensions.findTopReferrerHostClicks(linkId.value(), TOP_SIZE).stream()
            .map(r -> new LinkStats.ReferrerHostClick(r.getHost(), r.getCount()))
            .toList();
    Map<String, Long> channelTotals = new HashMap<>();
    referrers.forEach(
        r -> channelTotals.merge(channelClassifier.classify(r.referrer()), r.count(), Long::sum));
    long direct = clickTotals.countDirectByLinkId(linkId.value());
    if (direct > 0) channelTotals.merge("direct", direct, Long::sum);
    List<LinkStats.ChannelClick> channels =
        channelTotals.entrySet().stream()
            .map(e -> new LinkStats.ChannelClick(e.getKey(), e.getValue()))
            .sorted((a, b) -> Long.compare(b.count(), a.count()))
            .toList();
    return new ChannelBreakdowns(referrers, referrerHosts, channels);
  }

  DeviceBreakdowns devices(LinkId linkId) {
    List<LinkStats.DeviceClick> devices =
        clickDimensions.findDeviceClicks(linkId.value()).stream()
            .map(r -> new LinkStats.DeviceClick(orUnknown(r.getDevice()), r.getCount()))
            .toList();
    List<LinkStats.OsClick> os =
        clickDimensions.findTopOsClicks(linkId.value(), TOP_SIZE).stream()
            .map(r -> new LinkStats.OsClick(orUnknown(r.getOs()), r.getCount()))
            .toList();
    List<LinkStats.BrowserClick> browsers =
        clickDimensions.findTopBrowserClicks(linkId.value(), TOP_SIZE).stream()
            .map(r -> new LinkStats.BrowserClick(orUnknown(r.getBrowser()), r.getCount()))
            .toList();
    List<LinkStats.BotClick> botBreakdown =
        clickDimensions.findTopBotClicks(linkId.value(), TOP_SIZE).stream()
            .map(r -> new LinkStats.BotClick(orUnknown(r.getBot()), r.getCount()))
            .toList();
    return new DeviceBreakdowns(devices, os, browsers, botBreakdown);
  }

  UtmBreakdowns utm(LinkId linkId) {
    List<LinkStats.UtmCampaignClick> campaigns =
        clickDimensions.findTopUtmCampaignClicks(linkId.value(), TOP_SIZE).stream()
            .map(r -> new LinkStats.UtmCampaignClick(r.getCampaign(), r.getCount()))
            .toList();
    List<LinkStats.UtmSourceClick> sources =
        clickDimensions.findTopUtmSourceClicks(linkId.value(), TOP_SIZE).stream()
            .map(r -> new LinkStats.UtmSourceClick(r.getSource(), r.getCount()))
            .toList();
    List<LinkStats.UtmMediumClick> mediums =
        clickDimensions.findTopUtmMediumClicks(linkId.value(), TOP_SIZE).stream()
            .map(r -> new LinkStats.UtmMediumClick(r.getMedium(), r.getCount()))
            .toList();
    List<LinkStats.UtmContentClick> contents =
        clickDimensions.findTopUtmContentClicks(linkId.value(), TOP_SIZE).stream()
            .map(r -> new LinkStats.UtmContentClick(r.getContent(), r.getCount()))
            .toList();
    List<LinkStats.UtmTermClick> terms =
        clickDimensions.findTopUtmTermClicks(linkId.value(), TOP_SIZE).stream()
            .map(r -> new LinkStats.UtmTermClick(r.getTerm(), r.getCount()))
            .toList();
    List<LinkStats.SourceChannelClick> sourceChannels =
        clickDimensions.findTopSourceChannelClicks(linkId.value(), TOP_SIZE).stream()
            .map(r -> new LinkStats.SourceChannelClick(r.getSource(), r.getCount()))
            .toList();
    return new UtmBreakdowns(campaigns, sources, mediums, contents, terms, sourceChannels);
  }

  /** 인앱 브라우저별 사람 클릭. 일반 브라우저(client_app IS NULL)는 애초에 행이 없다. */
  List<LinkStats.ClientAppClick> clientApps(LinkId linkId) {
    return clickDimensions.findTopClientAppClicks(linkId.value(), TOP_SIZE).stream()
        .map(r -> new LinkStats.ClientAppClick(r.getApp(), r.getCount()))
        .toList();
  }

  /** Sec-Fetch-Site 값별 사람 클릭. 헤더를 안 보낸 클릭은 행이 없다(모르는 걸 direct 로 뭉개지 않는다). */
  List<LinkStats.FetchSiteClick> fetchSites(LinkId linkId) {
    return clickDimensions.findTopFetchSiteClicks(linkId.value(), TOP_SIZE).stream()
        .map(r -> new LinkStats.FetchSiteClick(r.getFetchSite(), r.getCount()))
        .toList();
  }

  /**
   * 링크를 품고 있던 글별 사람 클릭. 제목은 link 슬라이스가 post 를 직접 알면 슬라이스 그래프에 사이클이 생기므로 common 중립 포트({@link
   * PostTitleReader})로 한 번에 batch 조회해 붙인다. 지워진 글은 제목만 null 이고 클릭 수는 남긴다.
   */
  List<LinkStats.PostClick> postClicks(LinkId linkId) {
    var rows = clickDimensions.findTopPostClicks(linkId.value(), TOP_SIZE);
    if (rows.isEmpty()) return List.of();
    Map<Long, String> titles =
        postTitles.findTitlesByIds(rows.stream().map(r -> r.getPostId()).toList());
    return rows.stream()
        .map(r -> new LinkStats.PostClick(r.getPostId(), titles.get(r.getPostId()), r.getCount()))
        .toList();
  }

  GeoBreakdowns geo(LinkId linkId) {
    List<LinkStats.CountryClick> countries =
        clickDimensions.findTopCountryClicks(linkId.value(), TOP_SIZE).stream()
            .map(r -> new LinkStats.CountryClick(orUnknown(r.getCountry()), r.getCount()))
            .toList();
    List<LinkStats.RegionClick> regions =
        clickDimensions.findTopRegionClicks(linkId.value(), TOP_SIZE).stream()
            .map(r -> new LinkStats.RegionClick(r.getRegion(), r.getCount()))
            .toList();
    List<LinkStats.CityClick> cities =
        clickDimensions.findTopCityClicks(linkId.value(), TOP_SIZE).stream()
            .map(r -> new LinkStats.CityClick(r.getCity(), r.getCount()))
            .toList();
    List<LinkStats.LanguageClick> languages =
        clickDimensions.findTopLanguageClicks(linkId.value(), TOP_SIZE).stream()
            .map(r -> new LinkStats.LanguageClick(r.getLanguage(), r.getCount()))
            .toList();
    List<LinkStats.AsnClick> asns =
        clickDimensions.findTopAsnClicks(linkId.value(), TOP_SIZE).stream()
            .map(r -> new LinkStats.AsnClick(r.getAsn(), r.getOrganization(), r.getCount()))
            .toList();
    long datacenterClicks = clickTotals.countDatacenterClicks(linkId.value());
    return new GeoBreakdowns(countries, regions, cities, languages, asns, datacenterClicks);
  }

  List<LinkStats.DestinationClick> destinations(LinkEntity link) {
    var rows = clickDimensions.findDestinationClicks(link.linkId().value());
    if (rows.isEmpty()) return List.of();
    var byId = new HashMap<Long, LinkDestinationEntity>();
    destinationRepository
        .findAllByLinkIdOrderByIdAsc(link.linkId().value())
        .forEach(d -> byId.put(d.getId(), d));
    if (byId.isEmpty() && rows.size() == 1 && rows.get(0).getDestinationId() == null) {
      return List.of();
    }
    return rows.stream()
        .map(
            r -> {
              Long id = r.getDestinationId();
              if (id == null) {
                return new LinkStats.DestinationClick(
                    null, link.getOriginalUrl(), "default", 0, true, r.getCount());
              }
              var d = byId.get(id);
              if (d == null) {
                return new LinkStats.DestinationClick(
                    id, "(deleted)", null, 0, false, r.getCount());
              }
              return new LinkStats.DestinationClick(
                  d.getId(), d.getUrl(), d.getLabel(), d.getWeight(), d.isEnabled(), r.getCount());
            })
        .toList();
  }

  private static String orUnknown(String v) {
    return v == null ? "unknown" : v;
  }

  record ChannelBreakdowns(
      List<LinkStats.ReferrerClick> referrers,
      List<LinkStats.ReferrerHostClick> referrerHosts,
      List<LinkStats.ChannelClick> channels) {}

  record DeviceBreakdowns(
      List<LinkStats.DeviceClick> devices,
      List<LinkStats.OsClick> os,
      List<LinkStats.BrowserClick> browsers,
      List<LinkStats.BotClick> botBreakdown) {}

  record UtmBreakdowns(
      List<LinkStats.UtmCampaignClick> campaigns,
      List<LinkStats.UtmSourceClick> sources,
      List<LinkStats.UtmMediumClick> mediums,
      List<LinkStats.UtmContentClick> contents,
      List<LinkStats.UtmTermClick> terms,
      List<LinkStats.SourceChannelClick> sourceChannels) {}

  record GeoBreakdowns(
      List<LinkStats.CountryClick> countries,
      List<LinkStats.RegionClick> regions,
      List<LinkStats.CityClick> cities,
      List<LinkStats.LanguageClick> languages,
      List<LinkStats.AsnClick> asns,
      long datacenterClicks) {}
}
