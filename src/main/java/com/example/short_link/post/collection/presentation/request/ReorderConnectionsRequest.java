package com.example.short_link.post.collection.presentation.request;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 연결 순서 재배치 요청 — 이 컬렉션의 모든 연결 id 를 원하는 순서대로 빠짐없이 나열한다(스냅샷 재배치). PATH(reading path)에서 이 순서가 곧 논증의
 * 흐름이 된다.
 */
public record ReorderConnectionsRequest(@NotEmpty List<Long> connectionIds) {}
