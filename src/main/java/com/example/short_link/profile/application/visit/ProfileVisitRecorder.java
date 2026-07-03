package com.example.short_link.profile.application.visit;

import com.example.short_link.common.geoip.GeoLocation;
import com.example.short_link.link.application.dto.UserAgentInfo;
import com.example.short_link.link.classifier.application.AsnResolver;
import com.example.short_link.link.classifier.application.BotHeuristic;
import com.example.short_link.link.classifier.application.GeoIpResolver;
import com.example.short_link.link.classifier.application.UserAgentClassifier;
import com.example.short_link.link.classifier.application.helper.IpMasker;
import com.example.short_link.link.classifier.application.helper.LanguageExtractor;
import com.example.short_link.link.classifier.application.helper.ReferrerNormalizer;
import com.example.short_link.link.classifier.application.helper.SourceChannelNormalizer;
import com.example.short_link.link.classifier.application.helper.VisitorHasher;
import com.example.short_link.profile.domain.visit.ProfileVisitEntity;
import com.example.short_link.profile.domain.visit.ProfileVisitRepository;
import com.example.short_link.profile.exception.ProfileErrorCode;
import com.example.short_link.profile.exception.ProfileException;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records a {@link ProfileVisitEntity} for each visit to a /u/&lt;handle&gt; page. Reuses the same
 * UA / geo / ASN / bot-heuristic services as {@link
 * com.example.short_link.link.stats.application.ClickRecorder} so the enrichment quality (and the
 * downstream stats UX) stays consistent between link clicks and profile visits.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileVisitRecorder {

  private final ProfileVisitRepository repository;
  private final UserRepository userRepository;
  private final UserAgentClassifier userAgentClassifier;
  private final GeoIpResolver geoIpResolver;
  private final AsnResolver asnResolver;
  private final BotHeuristic botHeuristic;

  @Transactional
  public void recordUsername(
      String username,
      String referrer,
      String userAgent,
      String clientIp,
      String acceptLanguage,
      String sourceChannel,
      String utmSource,
      String utmMedium,
      String utmCampaign,
      String utmTerm,
      String utmContent,
      boolean gpc) {
    UserEntity owner =
        userRepository
            .findByUsername(username.toLowerCase())
            .orElseThrow(() -> new ProfileException(ProfileErrorCode.PROFILE_NOT_FOUND, username));
    record(
        owner.getId(),
        referrer,
        userAgent,
        clientIp,
        acceptLanguage,
        sourceChannel,
        utmSource,
        utmMedium,
        utmCampaign,
        utmTerm,
        utmContent,
        gpc);
  }

  @Transactional
  public void record(
      Long profileUserId,
      String referrer,
      String userAgent,
      String clientIp,
      String acceptLanguage,
      String sourceChannel,
      String utmSource,
      String utmMedium,
      String utmCampaign,
      String utmTerm,
      String utmContent,
      boolean gpc) {
    try {
      UserAgentInfo ua = userAgentClassifier.classify(userAgent);
      GeoLocation geo = geoIpResolver.resolve(clientIp);
      AsnResolver.AsnInfo asnInfo = asnResolver.resolve(clientIp);
      boolean uaBot = ua.bot();
      String botName = ua.botName();
      if (!uaBot && botHeuristic.isSuspectBurst(clientIp)) {
        uaBot = true;
        botName = BotHeuristic.SUSPECT_LABEL;
      } else if (!uaBot && asnInfo.datacenter()) {
        uaBot = true;
        botName =
            "datacenter:" + (asnInfo.organization() == null ? "unknown" : asnInfo.organization());
      }
      ProfileVisitEntity event =
          ProfileVisitEntity.builder()
              .profileUserId(profileUserId)
              .referrer(ReferrerNormalizer.normalize(referrer))
              .referrerHost(ReferrerNormalizer.hostOf(referrer))
              .userAgent(userAgent)
              .clientIp(IpMasker.mask(clientIp))
              .utmSource(utmSource)
              .utmMedium(utmMedium)
              .utmCampaign(utmCampaign)
              .utmTerm(utmTerm)
              .utmContent(utmContent)
              .deviceClass(ua.deviceClass())
              .osName(ua.osName())
              .browserName(ua.browserName())
              .bot(uaBot)
              .botName(botName)
              .countryCode(geo.countryCode())
              .regionName(geo.region())
              .cityName(geo.city())
              .language(LanguageExtractor.extract(acceptLanguage))
              // Sec-GPC(옵트아웃) 신호가 오면 재방문 식별 해시를 만들지 않는다 — "측정 위해 수집"이 아니라
              // "존중 위해 수집"(§0). 방문 자체는 익명 집계로 잡히되, 그 방문자는 return-tracking 안 함.
              .visitorHash(gpc ? null : VisitorHasher.hash(profileUserId, clientIp, userAgent))
              .sourceChannel(SourceChannelNormalizer.normalize(sourceChannel))
              .asn(asnInfo.asn())
              .asnOrg(asnInfo.organization())
              .build();
      repository.save(event);
    } catch (RuntimeException e) {
      log.warn("failed to record profile visit for userId={}", profileUserId, e);
    }
  }
}
