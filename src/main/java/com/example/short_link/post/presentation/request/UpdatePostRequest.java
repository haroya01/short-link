package com.example.short_link.post.presentation.request;

import com.example.short_link.post.domain.PostEntity;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * PATCH 의미. null = 변경 안 함. excerpt / ogImageUrl 은 빈 문자열로 explicit clear. tags 는 null = 변경 안 함, 빈 배열
 * = 전체 삭제 (서버가 trim / dedup / cap 정규화).
 */
public record UpdatePostRequest(
    // Blank allowed: a draft may be saved untitled (title is required only at publish). null = 변경 안
    // 함.
    @Size(max = 200) String title,
    @Size(min = 2, max = 200) String slug,
    @Size(max = 500) String excerpt,
    @Size(max = 512) String ogImageUrl,
    @Size(max = 256) String ogImageKey,
    @Size(max = 16) String languageTag,
    // 도메인 상한과 정렬: 초과분이 조용히 truncate/drop 되지 않고 명시적 400 으로 거부되도록.
    @Size(max = PostEntity.MAX_TAGS) List<@Size(max = PostEntity.MAX_TAG_LENGTH) String> tags) {}
