package com.example.short_link.profile.application.visit;

import com.example.short_link.common.geoip.GeoLocation;
import com.example.short_link.link.application.AsnResolver;
import com.example.short_link.link.application.BotHeuristic;
import com.example.short_link.link.application.GeoIpResolver;
import com.example.short_link.link.application.UserAgentClassifier;
import com.example.short_link.link.application.dto.UserAgentInfo;
import com.example.short_link.link.application.helper.IpMasker;
import com.example.short_link.link.application.helper.LanguageExtractor;
import com.example.short_link.link.application.helper.ReferrerNormalizer;
import com.example.short_link.link.application.helper.SourceChannelNormalizer;
import com.example.short_link.link.application.helper.VisitorHasher;
import com.example.short_link.profile.domain.visit.ProfileVisitEntity;
import com.example.short_link.profile.domain.visit.ProfileVisitRepository;
import com.example.short_link.profile.exception.ProfileNotFoundException;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records a {@link ProfileVisitEntity} for each visit to a /u/&lt;handle&gt; page. Reuses the same
 * UA / geo / ASN / bot-heuristic services as {@link
 * com.example.short_link.link.application.ClickRecorder} so the enrichment quality (and the
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
      String utmContent) {
    UserEntity owner =
        userRepository
            .findByUsername(username.toLowerCase())
            .orElseThrow(() -> new ProfileNotFoundException(username));
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
        utmContent);
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
      String utmContent) {
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
              .visitorHash(VisitorHasher.hash(profileUserId, clientIp, userAgent))
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
