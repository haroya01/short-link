package com.example.short_link.post.collection.presentation.request;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** 컬렉션의 전체 연결 ID를 원하는 순서대로 중복 없이 나열해야 한다. */
public record ReorderConnectionsRequest(@NotEmpty List<Long> connectionIds) {}
