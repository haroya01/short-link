package com.example.short_link.link.classifier.application;

import com.example.short_link.common.analytics.VisitRequest;
import com.example.short_link.common.geoip.GeoLocation;
import com.example.short_link.link.application.dto.UserAgentInfo;
import com.example.short_link.link.classifier.application.helper.IpMasker;
import com.example.short_link.link.classifier.application.helper.LanguageExtractor;
import com.example.short_link.link.classifier.application.helper.ReferrerNormalizer;
import com.example.short_link.link.classifier.application.helper.SourceChannelNormalizer;
import com.example.short_link.link.classifier.application.helper.VisitorHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VisitClassifier {

  private final UserAgentClassifier userAgentClassifier;
  private final GeoIpResolver geoIpResolver;
  private final AsnResolver asnResolver;
  private final BotClassifier botClassifier;

  public ClassifiedVisit classify(VisitRequest request, Long hashScopeId) {
    UserAgentInfo device = userAgentClassifier.classify(request.userAgent());
    GeoLocation geo = geoIpResolver.resolve(request.clientIp());
    AsnResolver.AsnInfo asn = asnResolver.resolve(request.clientIp());
    BotClassifier.Verdict bot = botClassifier.classify(device, asn, request.clientIp());
    return classified(request, hashScopeId, device, geo, asn, bot);
  }

  public ClassifiedVisit classifyKnownBot(VisitRequest request, Long hashScopeId, String botName) {
    UserAgentInfo device = userAgentClassifier.classify(request.userAgent());
    GeoLocation geo = geoIpResolver.resolve(request.clientIp());
    AsnResolver.AsnInfo asn = asnResolver.resolve(request.clientIp());
    return classified(request, hashScopeId, device, geo, asn, BotClassifier.Verdict.bot(botName));
  }

  private static ClassifiedVisit classified(
      VisitRequest request,
      Long hashScopeId,
      UserAgentInfo device,
      GeoLocation geo,
      AsnResolver.AsnInfo asn,
      BotClassifier.Verdict bot) {
    return new ClassifiedVisit(
        new ClassifiedVisit.Referrer(
            ReferrerNormalizer.normalize(request.referrer()),
            ReferrerNormalizer.hostOf(request.referrer())),
        IpMasker.mask(request.clientIp()),
        device,
        bot,
        geo,
        asn,
        LanguageExtractor.extract(request.acceptLanguage()),
        // GPC 수신 시 익명 방문만 집계하고 재방문 식별 해시는 만들지 않는다.
        request.gpc()
            ? null
            : VisitorHasher.hash(hashScopeId, request.clientIp(), request.userAgent()),
        SourceChannelNormalizer.normalize(request.sourceChannel()));
  }
}
