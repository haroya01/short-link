package com.example.short_link.common.eventlink;

/** 일시·장소 등 문구 포맷을 완료한 미리보기 값이다. 링크 리다이렉트는 그대로 태그에 사용한다. */
public record EventLinkPreview(String title, String description, String coverImageUrl) {}
