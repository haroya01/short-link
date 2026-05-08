package com.example.short_link.link.application;

import com.example.short_link.link.domain.ClickEventEntity;
import com.example.short_link.link.domain.ClickEventRepository;
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
  private final BotHeuristic botHeuristic;
  private final ApplicationEventPublisher events;

  @Transactional
  public void record(
      Long linkId,
      String originalUrl,
      String referrer,
      String userAgent,
      String clientIp,
      String acceptLanguage) {
    record(linkId, originalUrl, referrer, userAgent, clientIp, acceptLanguage, null, null);
  }

  @Transactional
  public void record(
      Long linkId,
      String originalUrl,
      String referrer,
      String userAgent,
      String clientIp,
      String acceptLanguage,
      String sourceChannel) {
    record(linkId, originalUrl, referrer, userAgent, clientIp, acceptLanguage, sourceChannel, null);
  }

  /**
   * Same as {@link #record} but forces the row to bot=true with a given name. Used for OG/preview
   * crawlers that yauaa doesn't classify as bots — we still want them in click_event so the stats
   * UI can show "social preview" volume separate from real clicks.
   */
  @Transactional
  public void recordPreview(
      Long linkId,
      String originalUrl,
      String referrer,
      String userAgent,
      String clientIp,
      String acceptLanguage,
      String sourceChannel,
      String crawlerLabel) {
    record(
        linkId,
        originalUrl,
        referrer,
        userAgent,
        clientIp,
        acceptLanguage,
        sourceChannel,
        PREVIEW_BOT_NAME_PREFIX + crawlerLabel);
  }

  @Transactional
  public void record(
      Long linkId,
      String originalUrl,
      String referrer,
      String userAgent,
      String clientIp,
      String acceptLanguage,
      String sourceChannel,
      String forcedBotName) {
    try {
      UtmParams utm = UtmExtractor.extract(originalUrl);
      UserAgentInfo ua = userAgentClassifier.classify(userAgent);
      GeoLocation geo = geoIpResolver.resolve(clientIp);
      boolean uaBot = ua.bot();
      String botName = ua.botName();
      if (forcedBotName != null) {
        uaBot = true;
        botName = forcedBotName;
      } else if (!uaBot && botHeuristic.isSuspectBurst(clientIp)) {
        uaBot = true;
        botName = BotHeuristic.SUSPECT_LABEL;
      }
      ClickEventEntity event =
          ClickEventEntity.builder()
              .linkId(linkId)
              .referrer(ReferrerNormalizer.normalize(referrer))
              .referrerHost(ReferrerNormalizer.hostOf(referrer))
              .userAgent(userAgent)
              .clientIp(IpMasker.mask(clientIp))
              .utmSource(utm.source())
              .utmMedium(utm.medium())
              .utmCampaign(utm.campaign())
              .utmTerm(utm.term())
              .utmContent(utm.content())
              .deviceClass(ua.deviceClass())
              .osName(ua.osName())
              .browserName(ua.browserName())
              .bot(uaBot)
              .botName(botName)
              .countryCode(geo.countryCode())
              .regionName(geo.regionName())
              .cityName(geo.cityName())
              .language(LanguageExtractor.extract(acceptLanguage))
              .visitorHash(VisitorHasher.hash(linkId, clientIp, userAgent))
              .sourceChannel(SourceChannelNormalizer.normalize(sourceChannel))
              .build();
      ClickEventEntity saved = repository.save(event);
      events.publishEvent(
          new ClickRecordedEvent(
              linkId,
              saved.getClickedAt() != null ? saved.getClickedAt() : Instant.now(),
              geo.countryCode(),
              ua.deviceClass(),
              ReferrerNormalizer.hostOf(referrer),
              uaBot));
    } catch (RuntimeException e) {
      log.warn("failed to record click for linkId={}", linkId, e);
    }
  }
}
