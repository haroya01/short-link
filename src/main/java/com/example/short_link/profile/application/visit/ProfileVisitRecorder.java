package com.example.short_link.profile.application.visit;

import com.example.short_link.common.analytics.VisitRequest;
import com.example.short_link.link.classifier.application.ClassifiedVisit;
import com.example.short_link.link.classifier.application.VisitClassifier;
import com.example.short_link.profile.domain.repository.ProfileVisitRepository;
import com.example.short_link.profile.domain.visit.ProfileVisitEntity;
import com.example.short_link.profile.exception.ProfileErrorCode;
import com.example.short_link.profile.exception.ProfileException;
import com.example.short_link.user.application.read.UserSummary;
import com.example.short_link.user.application.read.UserSummaryReader;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileVisitRecorder {

  private final ProfileVisitRepository repository;
  private final UserSummaryReader users;
  private final VisitClassifier visitClassifier;

  @Transactional
  public void recordUsername(String username, VisitRequest request) {
    UserSummary owner =
        users
            .findByUsername(username.toLowerCase(Locale.ROOT))
            .orElseThrow(() -> new ProfileException(ProfileErrorCode.PROFILE_NOT_FOUND, username));
    record(owner.id(), request);
  }

  @Transactional
  public void record(Long profileUserId, VisitRequest request) {
    ProfileVisitEntity event;
    try {
      ClassifiedVisit visit = visitClassifier.classify(request, profileUserId);
      event =
          ProfileVisitEntity.builder()
              .profileUserId(profileUserId)
              .referrer(visit.referrer().url())
              .referrerHost(visit.referrer().host())
              .userAgent(request.userAgent())
              .clientIp(visit.maskedIp())
              .utmSource(request.utm().source())
              .utmMedium(request.utm().medium())
              .utmCampaign(request.utm().campaign())
              .utmTerm(request.utm().term())
              .utmContent(request.utm().content())
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
              .asn(visit.asn().asn())
              .asnOrg(visit.asn().organization())
              .build();
    } catch (RuntimeException e) {
      log.warn("profile visit enrichment failed for userId={}", profileUserId, e);
      event = ProfileVisitEntity.builder().profileUserId(profileUserId).build();
    }
    repository.save(event);
  }
}
