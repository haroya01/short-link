package com.example.short_link.post.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PostSearchTermsTest {

  @Test
  void requiresEveryTerm() {
    assertThat(PostSearchTerms.booleanMatch("검색")).isEqualTo("+(검색)");
    assertThat(PostSearchTerms.booleanMatch("리다이렉트 성능")).isEqualTo("+(리다이렉트) +(성능)");
    assertThat(PostSearchTerms.booleanMatch("k-means 가속기")).isEqualTo("+(means) +(가속기)");
  }

  @Test
  void collapsesExtraWhitespace() {
    assertThat(PostSearchTerms.booleanMatch("  spring   jpa  ")).isEqualTo("+(spring) +(jpa)");
  }

  @Test
  void stripsFulltextOperatorCharsAndTheHashtagMark() {
    assertThat(PostSearchTerms.booleanMatch("+foo* -bar")).isEqualTo("+(foo) +(bar)");
    assertThat(PostSearchTerms.booleanMatch("\"quoted\"")).isEqualTo("+(quoted)");
    assertThat(PostSearchTerms.booleanMatch("(group)~12")).isEqualTo("+(group)");
    assertThat(PostSearchTerms.booleanMatch("#Java")).isEqualTo("+(Java)");
  }

  @Test
  void leavesOutTermsTheIndexCannotHoldOrWouldMatchInsideWords() {
    assertThat(PostSearchTerms.booleanMatch("AI 설계")).isEqualTo("+(설계)");
    assertThat(PostSearchTerms.booleanMatch("C++ 성능")).isEqualTo("+(성능)");
    assertThat(PostSearchTerms.booleanMatch("Go")).isEmpty();
  }

  @Test
  void operatorOnlyQueryBecomesEmpty() {
    // AGAINST('') 는 0건 — '모두 매칭'으로 새지 않으니 빈 문자열이면 충분.
    assertThat(PostSearchTerms.booleanMatch("+++ --- ***")).isEmpty();
    assertThat(PostSearchTerms.booleanMatch("   ")).isEmpty();
  }

  @Test
  void titleLikeFallbackEngagesOnlyForAllShortTerms() {
    // 모든 토큰이 두 글자 미만 → ngram 이 못 잡으므로 제목/요약 LIKE 폴백을 켠다(원문 이스케이프한 %…%).
    assertThat(PostSearchTerms.titleLikeFallback("C++")).isEqualTo("%c++%");
    assertThat(PostSearchTerms.titleLikeFallback("가")).isEqualTo("%가%");
  }

  @Test
  void titleLikeFallbackEngagesForTwoLetterLatinTerms() {
    assertThat(PostSearchTerms.titleLikeFallback("AI")).isEqualTo("%ai%");
    assertThat(PostSearchTerms.titleLikeFallback("ab")).isEqualTo("%ab%");
    assertThat(PostSearchTerms.titleLikeFallback("C#")).isEqualTo("%c#%");
  }

  @Test
  void wordsOnceLostToStopwordsGoToTheIndex() {
    assertThat(PostSearchTerms.titleLikeFallback("java")).isNull();
    assertThat(PostSearchTerms.titleLikeFallback("data")).isNull();
    assertThat(PostSearchTerms.titleLikeFallback("설계")).isNull();
  }

  @Test
  void titleWordFallbackGuardsAsciiEdgesOfTheFallbackPhrase() {
    // 영문·숫자로 시작·끝나는 폴백은 앞뒤가 영문·숫자가 아닐 때만 맞춘다(email 의 ai 제외).
    assertThat(PostSearchTerms.titleWordFallback("AI")).isEqualTo("(^|[^a-z0-9])ai([^a-z0-9]|$)");
    assertThat(PostSearchTerms.titleWordFallback("go")).isEqualTo("(^|[^a-z0-9])go([^a-z0-9]|$)");
    // 정규식 특수문자는 글자로, 영문·숫자가 아닌 끝에는 경계를 두지 않는다.
    assertThat(PostSearchTerms.titleWordFallback("C++")).isEqualTo("(^|[^a-z0-9])c\\+\\+");
  }

  @Test
  void titleWordFallbackOffWithoutAsciiEdgesOrFallback() {
    assertThat(PostSearchTerms.titleWordFallback("가")).isNull();
    assertThat(PostSearchTerms.titleWordFallback("docker")).isNull();
  }

  @Test
  void titleLikeFallbackOffWhenAnyTermVisibleToNgram() {
    // 색인에 남는 바이그램이 있는 항이 하나라도 있으면 MATCH가 맡는다(jpa의 jp, docker의 do).
    assertThat(PostSearchTerms.titleLikeFallback("리다이렉트")).isNull();
    assertThat(PostSearchTerms.titleLikeFallback("C++ 성능")).isNull();
    assertThat(PostSearchTerms.titleLikeFallback("jpa")).isNull();
    assertThat(PostSearchTerms.titleLikeFallback("docker")).isNull();
    assertThat(PostSearchTerms.titleLikeFallback("AI 성능")).isNull();
  }

  @Test
  void titleLikeFallbackNullForOperatorOnlyQuery() {
    assertThat(PostSearchTerms.titleLikeFallback("+++")).isNull();
    assertThat(PostSearchTerms.titleLikeFallback("   ")).isNull();
  }

  @Test
  void titleLikeFallbackEscapesWildcards() {
    // 짧은 질의라도 %/_ 는 리터럴로 — '모두 매칭' 으로 새지 않게 이스케이프한다.
    assertThat(PostSearchTerms.titleLikeFallback("%")).isEqualTo("%!%%");
    assertThat(PostSearchTerms.titleLikeFallback("_")).isEqualTo("%!_%");
  }

  @Test
  void latinEdgedTermsMustStandAsWordsAndKoreanOnesNeedNot() {
    assertThat(PostSearchTerms.matchWordBoundaries("java"))
        .isEqualTo("^(?=[\\s\\S]*(^|[^a-z0-9])java([^a-z0-9]|$))");
    assertThat(PostSearchTerms.matchWordBoundaries("Java 성능"))
        .isEqualTo("^(?=[\\s\\S]*(^|[^a-z0-9])java([^a-z0-9]|$))");
    assertThat(PostSearchTerms.matchWordBoundaries("node.js"))
        .isEqualTo("^(?=[\\s\\S]*(^|[^a-z0-9])node\\.js([^a-z0-9]|$))");
    assertThat(PostSearchTerms.matchWordBoundaries("성능 튜닝")).isNull();
    assertThat(PostSearchTerms.matchWordBoundaries("AI")).isNull();
  }
}
