package com.example.short_link.link.stats.application;

import com.example.short_link.common.geoip.GeoLocation;
import com.example.short_link.link.application.dto.ClickRecordedEvent;
import com.example.short_link.link.application.dto.UserAgentInfo;
import com.example.short_link.link.application.dto.UtmParams;
import com.example.short_link.link.classifier.application.AsnResolver;
import com.example.short_link.link.classifier.application.BotHeuristic;
import com.example.short_link.link.classifier.application.GeoIpResolver;
import com.example.short_link.link.classifier.application.UserAgentClassifier;
import com.example.short_link.link.classifier.application.helper.IpMasker;
import com.example.short_link.link.classifier.application.helper.LanguageExtractor;
import com.example.short_link.link.classifier.application.helper.ReferrerNormalizer;
import com.example.short_link.link.classifier.application.helper.SourceChannelNormalizer;
import com.example.short_link.link.classifier.application.helper.UtmExtractor;
import com.example.short_link.link.classifier.application.helper.VisitorHasher;
import com.example.short_link.link.stats.domain.ClickEventEntity;
import com.example.short_link.link.stats.domain.repository.ClickEventRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClickRecorder {

  /** Bot name prefix used when the row is a social/messenger preview crawler hit. */
  public static final String PREVIEW_BOT_NAME_PREFIX = "preview:";

  private final ClickEventRepository repository;
  private final UserAgentClassifier userAgentClassifier;
  private final GeoIpResolver geoIpResolver;
  private final AsnResolver asnResolver;
  private final BotHeuristic botHeuristic;
  private final ApplicationEventPublisher events;

  @Transactional
  public void record(ClickContext ctx) {
    save(ctx, null);
  }

  /**
   * Same as {@link #record} but forces the row to bot=true with a given name. Used for OG/preview
   * crawlers that yauaa doesn't classify as bots — we still want them in click_event so the stats
   * UI can show "social preview" volume separate from real clicks.
   */
  @Transactional
  public void recordPreview(ClickContext ctx, String crawlerLabel) {
    save(ctx, PREVIEW_BOT_NAME_PREFIX + crawlerLabel);
  }

  private void save(ClickContext ctx, String forcedBotName) {
    try {
      UtmParams utm = UtmExtractor.extract(ctx.originalUrl());
      UserAgentInfo ua = userAgentClassifier.classify(ctx.userAgent());
      GeoLocation geo = geoIpResolver.resolve(ctx.clientIp());
      AsnResolver.AsnInfo asnInfo = asnResolver.resolve(ctx.clientIp());
      BotClassification bot = classifyBot(ua, asnInfo, ctx.clientIp(), forcedBotName);
      ClickEventEntity event =
          ClickEventEntity.builder()
              .linkId(ctx.linkId())
              .referrer(ReferrerNormalizer.normalize(ctx.referrer()))
              .referrerHost(ReferrerNormalizer.hostOf(ctx.referrer()))
              .userAgent(ctx.userAgent())
              .clientIp(IpMasker.mask(ctx.clientIp()))
              .utmSource(utm.source())
              .utmMedium(utm.medium())
              .utmCampaign(utm.campaign())
              .utmTerm(utm.term())
              .utmContent(utm.content())
              .deviceClass(ua.deviceClass())
              .osName(ua.osName())
              .browserName(ua.browserName())
              .bot(bot.isBot())
              .botName(bot.botName())
              .countryCode(geo.countryCode())
              .regionName(geo.region())
              .cityName(geo.city())
              .language(LanguageExtractor.extract(ctx.acceptLanguage()))
              .visitorHash(VisitorHasher.hash(ctx.linkId(), ctx.clientIp(), ctx.userAgent()))
              .sourceChannel(SourceChannelNormalizer.normalize(ctx.sourceChannel()))
              .destinationId(ctx.destinationId())
              .asn(asnInfo.asn())
              .asnOrg(asnInfo.organization())
              .build();
      ClickEventEntity saved = repository.save(event);
      events.publishEvent(
          new ClickRecordedEvent(
              ctx.linkId(),
              saved.getClickedAt() != null ? saved.getClickedAt() : Instant.now(),
              geo.countryCode(),
              ua.deviceClass(),
              ReferrerNormalizer.hostOf(ctx.referrer()),
              bot.isBot(),
              utm.source()));
    } catch (RuntimeException e) {
      log.warn("failed to record click for linkId={}", ctx.linkId(), e);
    }
  }

  private BotClassification classifyBot(
      UserAgentInfo ua, AsnResolver.AsnInfo asnInfo, String clientIp, String forcedBotName) {
    if (forcedBotName != null) return new BotClassification(true, forcedBotName);
    if (ua.bot()) return new BotClassification(true, ua.botName());
    if (botHeuristic.isSuspectBurst(clientIp)) {
      return new BotClassification(true, BotHeuristic.SUSPECT_LABEL);
    }
    if (asnInfo.datacenter()) {
      // Datacenter ASN with non-bot UA → almost always a scraper/headless. Mark it so stats
      // don't conflate cloud egress with real eyeball traffic.
      String org = asnInfo.organization() == null ? "unknown" : asnInfo.organization();
      return new BotClassification(true, "datacenter:" + org);
    }
    return new BotClassification(false, null);
  }

  private record BotClassification(boolean isBot, String botName) {}
}
