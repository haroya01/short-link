package com.example.short_link.post.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PostRepositoryAdapterBooleanMatchTest {

  @Test
  void keepsPlainQueryVerbatim() {
    assertThat(PostRepositoryAdapter.booleanMatch("검색")).isEqualTo("검색");
    assertThat(PostRepositoryAdapter.booleanMatch("리다이렉트 성능")).isEqualTo("리다이렉트 성능");
  }

  @Test
  void collapsesExtraWhitespace() {
    assertThat(PostRepositoryAdapter.booleanMatch("  a   b  ")).isEqualTo("a b");
  }

  @Test
  void stripsFulltextOperatorChars() {
    // BOOLEAN 모드에서 하이재킹 방지용으로 연산자 문자를 공백으로 바꿔 토큰 경계로만 남긴다.
    assertThat(PostRepositoryAdapter.booleanMatch("+foo* -bar")).isEqualTo("foo bar");
    assertThat(PostRepositoryAdapter.booleanMatch("\"quoted\"")).isEqualTo("quoted");
    assertThat(PostRepositoryAdapter.booleanMatch("(group)~1")).isEqualTo("group 1");
  }

  @Test
  void operatorOnlyQueryBecomesEmpty() {
    // AGAINST('') 는 0건 — '모두 매칭'으로 새지 않으니 빈 문자열이면 충분.
    assertThat(PostRepositoryAdapter.booleanMatch("+++ --- ***")).isEmpty();
    assertThat(PostRepositoryAdapter.booleanMatch("   ")).isEmpty();
  }

  @Test
  void titleLikeFallbackEngagesOnlyForAllShortTerms() {
    // 모든 토큰이 두 글자 미만 → ngram 이 못 잡으므로 제목/요약 LIKE 폴백을 켠다(원문 이스케이프한 %…%).
    assertThat(PostRepositoryAdapter.titleLikeFallback("C++")).isEqualTo("%c++%");
    assertThat(PostRepositoryAdapter.titleLikeFallback("가")).isEqualTo("%가%");
  }

  @Test
  void titleLikeFallbackEngagesForStopwordDeadTerms() {
    // 모든 바이그램이 기본 스톱워드('a'·'i' 포함)에 걸리는 항은 ngram 색인에 남지 않는다.
    assertThat(PostRepositoryAdapter.titleLikeFallback("java")).isEqualTo("%java%");
    assertThat(PostRepositoryAdapter.titleLikeFallback("Java")).isEqualTo("%java%");
    assertThat(PostRepositoryAdapter.titleLikeFallback("data")).isEqualTo("%data%");
    // "ab" 도 유일 바이그램 "ab" 가 'a' 포함이라 인덱스 불가시.
    assertThat(PostRepositoryAdapter.titleLikeFallback("ab")).isEqualTo("%ab%");
  }

  @Test
  void titleWordFallbackGuardsAsciiEdgesOfTheFallbackPhrase() {
    // 영문·숫자로 시작·끝나는 폴백은 앞뒤가 영문·숫자가 아닐 때만 맞춘다(email 의 ai 제외).
    assertThat(PostRepositoryAdapter.titleWordFallback("AI"))
        .isEqualTo("(^|[^a-z0-9])ai([^a-z0-9]|$)");
    assertThat(PostRepositoryAdapter.titleWordFallback("java"))
        .isEqualTo("(^|[^a-z0-9])java([^a-z0-9]|$)");
    // 정규식 특수문자는 글자로, 영문·숫자가 아닌 끝에는 경계를 두지 않는다.
    assertThat(PostRepositoryAdapter.titleWordFallback("C++")).isEqualTo("(^|[^a-z0-9])c\\+\\+");
  }

  @Test
  void titleWordFallbackOffWithoutAsciiEdgesOrFallback() {
    assertThat(PostRepositoryAdapter.titleWordFallback("가")).isNull();
    assertThat(PostRepositoryAdapter.titleWordFallback("docker")).isNull();
  }

  @Test
  void titleLikeFallbackOffWhenAnyTermVisibleToNgram() {
    // 색인에 남는 바이그램이 있는 항이 하나라도 있으면 MATCH가 맡는다(jpa의 jp, docker의 do).
    assertThat(PostRepositoryAdapter.titleLikeFallback("리다이렉트")).isNull();
    assertThat(PostRepositoryAdapter.titleLikeFallback("C++ 성능")).isNull();
    assertThat(PostRepositoryAdapter.titleLikeFallback("jpa")).isNull();
    assertThat(PostRepositoryAdapter.titleLikeFallback("docker")).isNull();
    // 죽은 토큰(java)과 산 토큰(성능)이 섞이면 산 쪽을 MATCH 에 맡긴다.
    assertThat(PostRepositoryAdapter.titleLikeFallback("java 성능")).isNull();
  }

  @Test
  void titleLikeFallbackNullForOperatorOnlyQuery() {
    assertThat(PostRepositoryAdapter.titleLikeFallback("+++")).isNull();
    assertThat(PostRepositoryAdapter.titleLikeFallback("   ")).isNull();
  }

  @Test
  void titleLikeFallbackEscapesWildcards() {
    // 짧은 질의라도 %/_ 는 리터럴로 — '모두 매칭' 으로 새지 않게 이스케이프한다.
    assertThat(PostRepositoryAdapter.titleLikeFallback("%")).isEqualTo("%!%%");
    assertThat(PostRepositoryAdapter.titleLikeFallback("_")).isEqualTo("%!_%");
  }
}
