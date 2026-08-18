package com.example.short_link.common.eventlink;

import java.util.Optional;

/**
 * 링크 → 이벤트 미리보기 조회의 중립 포트. 링크 슬라이스가 이벤트 슬라이스를 직접 참조하면 슬라이스 순환이라(ArchUnit), 구현은 이벤트 슬라이스가 지고 소비는 링크
 * 리다이렉트가 한다.
 */
public interface EventLinkPreviewPort {

  Optional<EventLinkPreview> findByLinkId(long linkId);
}
