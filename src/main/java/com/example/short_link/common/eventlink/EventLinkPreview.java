package com.example.short_link.common.eventlink;

/**
 * 이벤트(모집)에 귀속된 단축 링크의 소셜 미리보기 재료. 문구 조립(일시·장소 포맷)은 이벤트 슬라이스가 끝내서 넘긴다 — 소비자(링크 리다이렉트)는 그대로 태그에 박기만
 * 한다.
 */
public record EventLinkPreview(String title, String description, String coverImageUrl) {}
