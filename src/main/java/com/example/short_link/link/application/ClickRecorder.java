package com.example.short_link.link.application;

import com.example.short_link.link.domain.ClickEventEntity;
import com.example.short_link.link.domain.ClickEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClickRecorder {

  private final ClickEventRepository repository;
  private final UserAgentClassifier userAgentClassifier;

  @Transactional
  public void record(
      Long linkId, String originalUrl, String referrer, String userAgent, String clientIp) {
    UtmParams utm = UtmExtractor.extract(originalUrl);
    UserAgentInfo ua = userAgentClassifier.classify(userAgent);
    ClickEventEntity event =
        ClickEventEntity.builder()
            .linkId(linkId)
            .referrer(referrer)
            .userAgent(userAgent)
            .clientIp(clientIp)
            .utmSource(utm.source())
            .utmMedium(utm.medium())
            .utmCampaign(utm.campaign())
            .utmTerm(utm.term())
            .utmContent(utm.content())
            .deviceClass(ua.deviceClass())
            .osName(ua.osName())
            .browserName(ua.browserName())
            .bot(ua.bot())
            .build();
    repository.save(event);
  }
}
