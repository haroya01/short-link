package com.example.short_link.common.eventlink;

import java.util.Optional;

/** 이벤트 슬라이스가 구현하며, 링크 리다이렉트와 이벤트 사이의 순환 의존을 막는다. */
public interface EventLinkPreviewPort {

  Optional<EventLinkPreview> findByLinkId(long linkId);
}
