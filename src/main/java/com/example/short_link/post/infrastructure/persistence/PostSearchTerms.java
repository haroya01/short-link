package com.example.short_link.post.infrastructure.persistence;

import java.util.Locale;

final class PostSearchTerms {

  private PostSearchTerms() {}

  // LIKE의 이스케이프 문자 !부터 처리해야 사용자의 !가 다음 문자에 영향을 주지 않는다.
  static String likePattern(String query) {
    String escaped =
        query.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_");
    return "%" + escaped + "%";
  }

  // BOOLEAN 연산자와 해시태그 #을 지워 검색어가 구문을 바꾸지 않게 하고, 색인에 남는 항마다 +( )로
  // 모두 요구한다. 괄호 없는 +는 한 바이그램짜리 항(설계)을 찾지 못한다.
  static String booleanMatch(String query) {
    StringBuilder match = new StringBuilder();
    for (String term : scrub(query).split(" ")) {
      if (indexed(term)) {
        match.append(match.isEmpty() ? "" : " ").append("+(").append(term).append(')');
      }
    }
    return match.toString();
  }

  private static String scrub(String query) {
    return query.replaceAll("[+\\-><()~*\"@#]", " ").replaceAll("\\s+", " ").trim();
  }

  static String matchWordBoundaries(String query) {
    StringBuilder pattern = new StringBuilder();
    for (String term : scrub(query).split(" ")) {
      if (!indexed(term)) {
        continue;
      }
      String word = term.toLowerCase(Locale.ROOT);
      boolean head = isAsciiAlnum(word.charAt(0));
      boolean tail = isAsciiAlnum(word.charAt(word.length() - 1));
      if (head || tail) {
        pattern
            .append("(?=[\\s\\S]*")
            .append(head ? "(^|[^a-z0-9])" : "")
            .append(regexLiteral(word))
            .append(tail ? "([^a-z0-9]|$)" : "")
            .append(')');
      }
    }
    return pattern.isEmpty() ? null : "^" + pattern;
  }

  // 두 글자 영문·숫자(AI·Go)는 ngram이 email·google 속까지 맞추므로 색인 대신 낱말 폴백에 둔다.
  private static boolean indexed(String term) {
    if (term.length() < 2) return false;
    if (term.length() > 2) return true;
    String lower = term.toLowerCase(Locale.ROOT);
    return !(isAsciiAlnum(lower.charAt(0)) && isAsciiAlnum(lower.charAt(1)));
  }

  static String titleLikeFallback(String query) {
    String scrubbed = scrub(query);
    if (scrubbed.isEmpty()) {
      return null;
    }
    for (String term : scrubbed.split(" ")) {
      if (indexed(term)) {
        return null;
      }
    }
    return likePattern(query);
  }

  // 영문·숫자로 시작하거나 끝나는 폴백 질의는 낱말 속(email·domain의 ai, javascript의 java)에
  // 걸리지 않게 앞뒤가 영문·숫자가 아닐 때만 맞춘다. 한글 조사가 붙은 'AI가'·'Java로'는 걸린다.
  static String titleWordFallback(String query) {
    if (titleLikeFallback(query) == null) {
      return null;
    }
    String phrase = query.toLowerCase(Locale.ROOT);
    boolean head = isAsciiAlnum(phrase.charAt(0));
    boolean tail = isAsciiAlnum(phrase.charAt(phrase.length() - 1));
    if (!head && !tail) {
      return null;
    }
    return (head ? "(^|[^a-z0-9])" : "") + regexLiteral(phrase) + (tail ? "([^a-z0-9]|$)" : "");
  }

  private static boolean isAsciiAlnum(char c) {
    return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
  }

  private static String regexLiteral(String text) {
    StringBuilder out = new StringBuilder(text.length() * 2);
    for (char c : text.toCharArray()) {
      if ("\\^$.|?*+()[]{}".indexOf(c) >= 0) {
        out.append('\\');
      }
      out.append(c);
    }
    return out.toString();
  }
}
