package com.example.short_link.event.application;

import java.util.Optional;

/** 공개 페이지의 신뢰 신호용 주최자 표시 정보 — 계정 없는 방문자가 "누가 여는 모임인지"를 본다. */
public interface EventOrganizerLookup {

  record Organizer(String username, String avatarUrl) {}

  Optional<Organizer> find(Long userId);
}
