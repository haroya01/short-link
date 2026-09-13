package com.example.short_link.event.application;

import java.util.List;
import java.util.Optional;

/** 이벤트 링크의 가장 최근 human 클릭을 링크별 IP+UA 해시로 매칭한다. */
public interface RegistrationClickLookup {

  record ClickSnapshot(
      Long linkId, String sourceChannel, String clientApp, String referrerHost, String utmSource) {}

  record LinkVisitor(Long linkId, String visitorHash) {}

  Optional<ClickSnapshot> findLatestHumanClick(List<LinkVisitor> candidates);
}
