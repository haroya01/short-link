package com.example.short_link.profile.presentation.request;

import jakarta.validation.constraints.Size;

// 도메인 BlockContentValidator 가 타입별 정밀 검증을 하므로 여기 @Size 는 도메인 상한(16384)에 맞춘
// 바깥 안전선. (과거 max=120 이 이미지 URL·JSON 블록 수정을 전부 400 으로 막던 P0 버그 해소.)
public record ProfileBlockUpdateRequest(@Size(max = 16384) String content) {}
