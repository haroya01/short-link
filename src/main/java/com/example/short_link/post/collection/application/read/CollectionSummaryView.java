package com.example.short_link.post.collection.application.read;

import java.time.Instant;
import java.util.List;

/**
 * 컬렉션 목록 한 줄 — 제목·소개·공개범위·담긴 수 + 최근 항목 미리보기(어디 넣을지 떠올리게). 좋아요·팔로워 수 없음(§0 바깥은 조용히). {@code preview}
 * = 최근 담긴 항목 라벨 몇 개.
 *
 * <p>"이 글이 속한 길" 조회에서는 카테고리가 아니라 "@큐레이터의 길 · N편 중 M번째"로 읽히도록 {@code curatorUsername}·{@code
 * curatorAvatarUrl}(그 컬렉션의 소유자)와 {@code position}(그 글이 이 길의 정렬된 연결 중 1-based 몇 번째인지, 분모는 {@code
 * count})을 함께 싣는다. 대상 글이 없는 목록판(내 컬렉션·큐레이터 공개 컬렉션)에서는 {@code position} 이 null 이다.
 *
 * <p>{@code connectionId} 는 내 컬렉션 목록을 특정 블록(blockType×refId) 기준으로 물었을 때(연결 시트) 그 블록이 이미 이 컬렉션에 이어져
 * 있으면 그 연결의 PK — 시트가 "이미 담김"을 표시하고 그 자리에서 끊게 한다. 그 외 조회에선 null.
 */
public record CollectionSummaryView(
    Long id,
    String title,
    String description,
    String visibility,
    String kind,
    int count,
    Instant updatedAt,
    List<String> preview,
    String curatorUsername,
    String curatorAvatarUrl,
    Integer position,
    Long connectionId) {}
