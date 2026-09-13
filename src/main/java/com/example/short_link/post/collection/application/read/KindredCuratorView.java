package com.example.short_link.post.collection.application.read;

import com.example.short_link.post.application.read.PublicAuthorView;

/** {@code sharedItems}는 두 큐레이터의 공개 컬렉션에 공통으로 담긴 블록 수다. */
public record KindredCuratorView(PublicAuthorView curator, int sharedItems) {}
