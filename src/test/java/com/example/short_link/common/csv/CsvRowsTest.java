package com.example.short_link.common.csv;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CsvRowsTest {

  @Test
  void readsMastodonsFollowingFile() {
    assertThat(
            CsvRows.parse(
                "﻿Account address,Show boosts,Notify on new posts,Languages\r\n"
                    + "sori@kurl.me,true,false,\r\n\r\nalice@mastodon.social,false,true,en\n"))
        .containsExactly(
            List.of("Account address", "Show boosts", "Notify on new posts", "Languages"),
            List.of("sori@kurl.me", "true", "false", ""),
            List.of("alice@mastodon.social", "false", "true", "en"));
  }

  @Test
  void aQuotedFieldKeepsCommasQuotesAndLineBreaks() {
    assertThat(CsvRows.parse("\"friends, close\",sori@kurl.me\n\"say \"\"hi\"\"\nthere\",x"))
        .containsExactly(
            List.of("friends, close", "sori@kurl.me"), List.of("say \"hi\"\nthere", "x"));
  }

  @Test
  void aLastLineWithoutABreakAndBlankLinesAreHandled() {
    assertThat(CsvRows.parse("spam.example\n\n  \nother.example"))
        .containsExactly(List.of("spam.example"), List.of("other.example"));
    assertThat(CsvRows.parse("")).isEmpty();
  }
}
