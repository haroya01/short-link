package com.example.short_link.post.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link PostRepositoryAdapter#booleanMatch(String)} 가 원시 검색어를 FULLTEXT BOOLEAN 모드용 AGAINST 문자열로
 * 안전하게 다듬는지. 연산자 제거·공백 정규화·빈 결과가 핵심.
 */
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
  void titleLikeFallbackOffWhenAnyTermLongEnough() {
    // 두 글자 이상 토큰이 하나라도 있으면 ngram 이 잡을 수 있으니 폴백은 꺼둔다(null = 쿼리 가지 off).
    assertThat(PostRepositoryAdapter.titleLikeFallback("리다이렉트")).isNull();
    assertThat(PostRepositoryAdapter.titleLikeFallback("C++ 성능")).isNull();
    assertThat(PostRepositoryAdapter.titleLikeFallback("ab")).isNull();
  }

  @Test
  void titleLikeFallbackNullForOperatorOnlyQuery() {
    // 스크럽 후 잡을 자연어가 없으면 폴백해도 소용없으니 끈다.
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
