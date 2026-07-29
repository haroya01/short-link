package com.example.short_link.event.application;

import java.util.List;
import java.util.Optional;

/**
 * 신청 직전 클릭을 찾는 캐퍼빌리티 포트. 방문자 해시(링크별 IP+UA)로 이벤트의 단축링크 클릭 중 가장 최근 human 클릭을 매칭 — 리다이렉트 경로에 손대지 않고
 * "어느 채널에서 온 신청인가"를 잇는다.
 */
public interface RegistrationClickLookup {

  record ClickSnapshot(
      Long linkId, String sourceChannel, String clientApp, String referrerHost, String utmSource) {}

  record LinkVisitor(Long linkId, String visitorHash) {}

  Optional<ClickSnapshot> findLatestHumanClick(List<LinkVisitor> candidates);
}
