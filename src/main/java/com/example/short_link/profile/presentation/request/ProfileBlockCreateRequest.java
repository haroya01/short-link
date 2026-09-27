package com.example.short_link.profile.presentation.request;

import jakarta.validation.constraints.Size;

// 타입별 검증은 BlockContentValidator가 담당한다. @Size는 가장 큰 도메인 입력 상한을 허용해야 한다.
public record ProfileBlockCreateRequest(String type, @Size(max = 16384) String content) {}
