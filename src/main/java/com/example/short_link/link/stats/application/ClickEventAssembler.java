package com.example.short_link.link.stats.application;

import com.example.short_link.common.analytics.Utm;
import com.example.short_link.common.analytics.VisitRequest;
import com.example.short_link.link.application.dto.UtmParams;
import com.example.short_link.link.classifier.application.ClassifiedVisit;
import com.example.short_link.link.classifier.application.ClientAppClassifier;
import com.example.short_link.link.classifier.application.VisitClassifier;
import com.example.short_link.link.classifier.application.helper.UtmExtractor;
import com.example.short_link.link.stats.domain.ClickEventEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ClickEventAssembler {

  private final VisitClassifier visitClassifier;
  private final ClientAppClassifier clientAppClassifier;

  public ClickEventEntity assemble(PendingClick click) {
    ClickContext ctx = click.ctx();
    UtmParams utm = UtmExtractor.extract(ctx.originalUrl());
    VisitRequest request =
        new VisitRequest(
            ctx.referrer(),
            ctx.userAgent(),
            ctx.clientIp(),
            ctx.acceptLanguage(),
            ctx.sourceChannel(),
            Utm.none(),
            ctx.gpc(),
            null);
    Long hashScope = ctx.linkId() == null ? null : ctx.linkId().value();
    ClassifiedVisit visit =
        click.forcedBotName() != null
            ? visitClassifier.classifyKnownBot(request, hashScope, click.forcedBotName())
            : visitClassifier.classify(request, hashScope);
    return ClickEventEntity.builder()
        .linkId(ctx.linkId())
        .clickedAt(click.occurredAt())
        .referrer(visit.referrer().url())
        .referrerHost(visit.referrer().host())
        .userAgent(ctx.userAgent())
        .clientIp(visit.maskedIp())
        .utmSource(utm.source())
        .utmMedium(utm.medium())
        .utmCampaign(utm.campaign())
        .utmTerm(utm.term())
        .utmContent(utm.content())
        .deviceClass(visit.device().deviceClass())
        .osName(visit.device().osName())
        .browserName(visit.device().browserName())
        .bot(visit.bot().isBot())
        .botName(visit.bot().botName())
        .countryCode(visit.geo().countryCode())
        .regionName(visit.geo().region())
        .cityName(visit.geo().city())
        .language(visit.language())
        .visitorHash(visit.visitorHash())
        .sourceChannel(visit.sourceChannel())
        .destinationId(ctx.destinationId())
        .postId(ctx.postId())
        .asn(visit.asn().asn())
        .asnOrg(visit.asn().organization())
        .clientApp(clientAppClassifier.classify(ctx.userAgent()))
        .fetchSite(ctx.fetchSite())
        .build();
  }
}
