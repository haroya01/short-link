package com.example.short_link.note.application.read;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NoteSearchTermsTest {

  @Test
  void aQueryTheNgramIndexHoldsSearchesItWithoutOperators() {
    assertThat(NoteSearchTerms.of("헥사고날 포트")).isEqualTo(new NoteSearchTerms("헥사고날 포트", null));
    assertThat(NoteSearchTerms.of("+spring -boot \"jpa\"").match()).isEqualTo("spring boot jpa");
  }

  @Test
  void aQueryTooShortOrMadeOfStopwordBigramsFallsBackToAnEscapedLike() {
    assertThat(NoteSearchTerms.of("밥")).isEqualTo(new NoteSearchTerms(null, "%밥%"));
    assertThat(NoteSearchTerms.of("AI")).isEqualTo(new NoteSearchTerms(null, "%ai%"));
    assertThat(NoteSearchTerms.of("is it")).isEqualTo(new NoteSearchTerms(null, "%is it%"));
    assertThat(NoteSearchTerms.of("C++").like()).isEqualTo("%c++%");
    assertThat(NoteSearchTerms.of("%").like()).isEqualTo("%!%%");
    assertThat(NoteSearchTerms.of("_").like()).isEqualTo("%!_%");
    assertThat(NoteSearchTerms.of("!").like()).isEqualTo("%!!%");
  }
}
