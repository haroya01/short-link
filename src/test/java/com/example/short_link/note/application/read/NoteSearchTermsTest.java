package com.example.short_link.note.application.read;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NoteSearchTermsTest {

  @Test
  void aQueryTheNgramIndexHoldsRequiresEveryTermWithoutTheUsersOperators() {
    assertThat(NoteSearchTerms.of("헥사고날 포트")).isEqualTo(new NoteSearchTerms("+(헥사고날) +(포트)", null));
    assertThat(NoteSearchTerms.of("+spring -boot \"jpa\"").match())
        .isEqualTo("+(spring) +(boot) +(jpa)");
    assertThat(NoteSearchTerms.of("#Java").match()).isEqualTo("+(Java)");
    assertThat(NoteSearchTerms.of("data").match()).isEqualTo("+(data)");
    assertThat(NoteSearchTerms.of("AI 설계").match()).isEqualTo("+(설계)");
  }

  @Test
  void aQueryTooShortOrOfTwoLetterLatinTermsFallsBackToAnEscapedLike() {
    assertThat(NoteSearchTerms.of("밥")).isEqualTo(new NoteSearchTerms(null, "%밥%"));
    assertThat(NoteSearchTerms.of("AI")).isEqualTo(new NoteSearchTerms(null, "%ai%"));
    assertThat(NoteSearchTerms.of("is it")).isEqualTo(new NoteSearchTerms(null, "%is it%"));
    assertThat(NoteSearchTerms.of("C++").like()).isEqualTo("%c++%");
    assertThat(NoteSearchTerms.of("%").like()).isEqualTo("%!%%");
    assertThat(NoteSearchTerms.of("_").like()).isEqualTo("%!_%");
    assertThat(NoteSearchTerms.of("!").like()).isEqualTo("%!!%");
  }
}
