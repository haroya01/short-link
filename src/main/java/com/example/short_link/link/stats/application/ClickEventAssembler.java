package com.example.short_link.link.stats.application;

import com.example.short_link.common.geoip.GeoLocation;
import com.example.short_link.link.application.dto.UserAgentInfo;
import com.example.short_link.link.application.dto.UtmParams;
import com.example.short_link.link.classifier.application.AsnResolver;
import com.example.short_link.link.classifier.application.BotHeuristic;
import com.example.short_link.link.classifier.application.ClientAppClassifier;
import com.example.short_link.link.classifier.application.GeoIpResolver;
import com.example.short_link.link.classifier.application.UserAgentClassifier;
import com.example.short_link.link.classifier.application.helper.IpMasker;
import com.example.short_link.link.classifier.application.helper.LanguageExtractor;
import com.example.short_link.link.classifier.application.helper.ReferrerNormalizer;
import com.example.short_link.link.classifier.application.helper.SourceChannelNormalizer;
import com.example.short_link.link.classifier.application.helper.UtmExtractor;
import com.example.short_link.link.classifier.application.helper.VisitorHasher;
import com.example.short_link.link.stats.domain.ClickEventEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Maps one {@link PendingClick} to a persistable {@link ClickEventEntity} — classification (UA,
 * GeoIP, ASN, bot) and privacy handling (IP masking, GPC opt-out) all live here. Knows nothing
 * about queues, transactions, or persistence.
 */
@Component
@RequiredArgsConstructor
public class ClickEventAssembler {

  private final UserAgentClassifier userAgentClassifier;
  private final ClientAppClassifier clientAppClassifier;
  private final GeoIpResolver geoIpResolver;
  private final AsnResolver asnResolver;
  private final BotHeuristic botHeuristic;

  public ClickEventEntity assemble(PendingClick click) {
    ClickContext ctx = click.ctx();
    UtmParams utm = UtmExtractor.extract(ctx.originalUrl());
    UserAgentInfo ua = userAgentClassifier.classify(ctx.userAgent());
    GeoLocation geo = geoIpResolver.resolve(ctx.clientIp());
    AsnResolver.AsnInfo asnInfo = asnResolver.resolve(ctx.clientIp());
    BotClassification bot = classifyBot(ua, asnInfo, ctx.clientIp(), click.forcedBotName());
    return ClickEventEntity.builder()
        .linkId(ctx.linkId())
        .clickedAt(click.occurredAt())
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
        // Sec-GPC(옵트아웃) 신호가 오면 재방문 식별 해시를 만들지 않는다 — "측정 위해 수집"이 아니라
        // "존중 위해 수집"(§0). 클릭 자체는 익명 집계로 잡히되, 그 방문자는 return-tracking 안 함.
        .visitorHash(
            ctx.gpc()
                ? null
                : VisitorHasher.hash(
                    ctx.linkId() == null ? null : ctx.linkId().value(),
                    ctx.clientIp(),
                    ctx.userAgent()))
        .sourceChannel(SourceChannelNormalizer.normalize(ctx.sourceChannel()))
        .destinationId(ctx.destinationId())
        .postId(ctx.postId())
        .asn(asnInfo.asn())
        .asnOrg(asnInfo.organization())
        // 인앱 브라우저 이름은 사람/봇과 별개 축이다 — 위 classifyBot 결과에 절대 섞지 않는다.
        .clientApp(clientAppClassifier.classify(ctx.userAgent()))
        .fetchSite(ctx.fetchSite())
        .build();
  }

  private BotClassification classifyBot(
      UserAgentInfo ua, AsnResolver.AsnInfo asnInfo, String clientIp, String forcedBotName) {
    if (forcedBotName != null) {
      return new BotClassification(true, forcedBotName);
    }
    if (ua.bot()) {
      return new BotClassification(true, ua.botName());
    }
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
