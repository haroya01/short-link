package com.example.short_link.post.domain;

import java.util.Set;

// 웹 app/[locale]/p/[username]/ 아래의 고정 경로 이름. 같은 이름의 글 주소는 그 경로에 가려 웹에서 열리지 않는다.
public final class ProfilePathSlugs {

  private static final Set<String> RESERVED =
      Set.of(
          "about",
          "bookmarks",
          "collections",
          "feed",
          "liked",
          "media",
          "notes",
          "opengraph-image",
          "replies",
          "reposts",
          "series");

  private ProfilePathSlugs() {}

  public static boolean isReserved(String slug) {
    return slug != null && RESERVED.contains(slug);
  }
}
