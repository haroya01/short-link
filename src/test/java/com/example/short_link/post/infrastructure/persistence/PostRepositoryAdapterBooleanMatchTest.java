package com.example.short_link.post.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PostRepositoryAdapterBooleanMatchTest {

  @Test
  void requiresEveryTerm() {
    assertThat(PostRepositoryAdapter.booleanMatch("검색")).isEqualTo("+(검색)");
    assertThat(PostRepositoryAdapter.booleanMatch("리다이렉트 성능")).isEqualTo("+(리다이렉트) +(성능)");
    assertThat(PostRepositoryAdapter.booleanMatch("k-means 가속기")).isEqualTo("+(means) +(가속기)");
  }

  @Test
  void collapsesExtraWhitespace() {
    assertThat(PostRepositoryAdapter.booleanMatch("  spring   jpa  "))
        .isEqualTo("+(spring) +(jpa)");
  }

  @Test
  void stripsFulltextOperatorCharsAndTheHashtagMark() {
    assertThat(PostRepositoryAdapter.booleanMatch("+foo* -bar")).isEqualTo("+(foo) +(bar)");
    assertThat(PostRepositoryAdapter.booleanMatch("\"quoted\"")).isEqualTo("+(quoted)");
    assertThat(PostRepositoryAdapter.booleanMatch("(group)~12")).isEqualTo("+(group)");
    assertThat(PostRepositoryAdapter.booleanMatch("#Java")).isEqualTo("+(Java)");
  }

  @Test
  void leavesOutTermsTheIndexCannotHoldOrWouldMatchInsideWords() {
    assertThat(PostRepositoryAdapter.booleanMatch("AI 설계")).isEqualTo("+(설계)");
    assertThat(PostRepositoryAdapter.booleanMatch("C++ 성능")).isEqualTo("+(성능)");
    assertThat(PostRepositoryAdapter.booleanMatch("Go")).isEmpty();
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
  void titleLikeFallbackEngagesForTwoLetterLatinTerms() {
    assertThat(PostRepositoryAdapter.titleLikeFallback("AI")).isEqualTo("%ai%");
    assertThat(PostRepositoryAdapter.titleLikeFallback("ab")).isEqualTo("%ab%");
    assertThat(PostRepositoryAdapter.titleLikeFallback("C#")).isEqualTo("%c#%");
  }

  @Test
  void wordsOnceLostToStopwordsGoToTheIndex() {
    assertThat(PostRepositoryAdapter.titleLikeFallback("java")).isNull();
    assertThat(PostRepositoryAdapter.titleLikeFallback("data")).isNull();
    assertThat(PostRepositoryAdapter.titleLikeFallback("설계")).isNull();
  }

  @Test
  void titleWordFallbackGuardsAsciiEdgesOfTheFallbackPhrase() {
    // 영문·숫자로 시작·끝나는 폴백은 앞뒤가 영문·숫자가 아닐 때만 맞춘다(email 의 ai 제외).
    assertThat(PostRepositoryAdapter.titleWordFallback("AI"))
        .isEqualTo("(^|[^a-z0-9])ai([^a-z0-9]|$)");
    assertThat(PostRepositoryAdapter.titleWordFallback("go"))
        .isEqualTo("(^|[^a-z0-9])go([^a-z0-9]|$)");
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
    assertThat(PostRepositoryAdapter.titleLikeFallback("AI 성능")).isNull();
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

  @Test
  void latinEdgedTermsMustStandAsWordsAndKoreanOnesNeedNot() {
    assertThat(PostRepositoryAdapter.matchWordBoundaries("java"))
        .isEqualTo("^(?=[\\s\\S]*(^|[^a-z0-9])java([^a-z0-9]|$))");
    assertThat(PostRepositoryAdapter.matchWordBoundaries("Java 성능"))
        .isEqualTo("^(?=[\\s\\S]*(^|[^a-z0-9])java([^a-z0-9]|$))");
    assertThat(PostRepositoryAdapter.matchWordBoundaries("node.js"))
        .isEqualTo("^(?=[\\s\\S]*(^|[^a-z0-9])node\\.js([^a-z0-9]|$))");
    assertThat(PostRepositoryAdapter.matchWordBoundaries("성능 튜닝")).isNull();
    assertThat(PostRepositoryAdapter.matchWordBoundaries("AI")).isNull();
  }
}
