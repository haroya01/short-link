package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.application.helper.CsvWriter;
import org.junit.jupiter.api.Test;

class CsvWriterTest {

  @Test
  void escapesValuesWithCommaQuoteOrNewline() {
    assertThat(CsvWriter.escape("plain")).isEqualTo("plain");
    assertThat(CsvWriter.escape("a,b")).isEqualTo("\"a,b\"");
    assertThat(CsvWriter.escape("a\"b")).isEqualTo("\"a\"\"b\"");
    assertThat(CsvWriter.escape("a\nb")).isEqualTo("\"a\nb\"");
  }

  @Test
  void escapesNullToEmpty() {
    assertThat(CsvWriter.escape(null)).isEmpty();
  }

  @Test
  void appendsRowJoinedByCommaAndTerminatedByNewline() {
    StringBuilder sb = new StringBuilder();
    CsvWriter.appendRow(sb, "a", "b,c", 42);
    assertThat(sb.toString()).isEqualTo("a,\"b,c\",42\n");
  }
}
