package com.example.short_link.profile.presentation.request;

import jakarta.validation.constraints.Size;

// content 의 정밀 검증(타입별 길이/형식)은 도메인 BlockContentValidator 가 수행한다. 여기 @Size 는
// 도메인 상한(타입별 2048, PRODUCT_CARD 16384)과 어긋나면 안 되는 바깥 안전선 — 과거 max=120 이
// S3 이미지 URL·JSON config 블록을 전부 400 으로 막던 버그를 도메인 ceiling 에 맞춰 해소.
public record ProfileBlockCreateRequest(String type, @Size(max = 16384) String content) {}
