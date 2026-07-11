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
}
