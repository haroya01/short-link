package com.example.short_link.post.application.read;

import com.example.short_link.common.post.PostLock;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class PostLockedNotice {

  private static final List<String> LANGUAGES = List.of("ko", "en", "ja");

  private static final Map<String, Map<PostLock, String>> NOTICES =
      Map.of(
          "ko",
          Map.of(
              PostLock.FOLLOWERS, "팔로워만 볼 수 있는 글이에요",
              PostLock.PASSWORD, "비밀번호가 걸린 글이에요",
              PostLock.REGION, "이 나라에서는 볼 수 없는 글이에요"),
          "en",
          Map.of(
              PostLock.FOLLOWERS, "Only followers can read this post.",
              PostLock.PASSWORD, "This post is protected by a password.",
              PostLock.REGION, "This post isn't available in your country."),
          "ja",
          Map.of(
              PostLock.FOLLOWERS, "フォロワーだけが読める記事です",
              PostLock.PASSWORD, "パスワードがかかった記事です",
              PostLock.REGION, "この国では読めない記事です"));

  private PostLockedNotice() {}

  static String text(PostLock lock, String acceptLanguage) {
    PostLock shown = lock == PostLock.REGION_CHECK ? PostLock.REGION : lock;
    return NOTICES.get(language(acceptLanguage)).get(shown);
  }

  private static String language(String acceptLanguage) {
    if (acceptLanguage == null || acceptLanguage.isBlank()) return "ko";
    try {
      String match = Locale.lookupTag(Locale.LanguageRange.parse(acceptLanguage), LANGUAGES);
      return match == null ? "ko" : match;
    } catch (IllegalArgumentException malformed) {
      return "ko";
    }
  }
}
