package com.example.short_link.post.collection.application.read;

import com.example.short_link.post.application.read.PublicAuthorView;

/**
 * 취향이 겹치는 큐레이터 — 같은 블록을 자기 공개 컬렉션에도 엮은 사람. {@code sharedItems} = 겹치는 블록 수. 팔로우가 아니라 *무엇을 엮었나* 로 잇는
 * 발견(Are.na — connect not broadcast).
 */
public record KindredCuratorView(PublicAuthorView curator, int sharedItems) {}
