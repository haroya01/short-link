package com.example.short_link.common.csv;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CsvCellsTest {

  @Test
  void escapesValuesWithCommaQuoteOrLineBreak() {
    assertThat(CsvCells.escape("plain")).isEqualTo("plain");
    assertThat(CsvCells.escape("a,b")).isEqualTo("\"a,b\"");
    assertThat(CsvCells.escape("a\"b")).isEqualTo("\"a\"\"b\"");
    assertThat(CsvCells.escape("a\nb")).isEqualTo("\"a\nb\"");
    assertThat(CsvCells.escape("a\rb")).isEqualTo("\"a\rb\"");
  }

  @Test
  void escapesNullToEmpty() {
    assertThat(CsvCells.escape(null)).isEmpty();
  }

  @Test
  void textThatASpreadsheetWouldRunAsAFormulaStaysText() {
    assertThat(CsvCells.escape("=1+1")).isEqualTo("'=1+1");
    assertThat(CsvCells.escape("+82")).isEqualTo("'+82");
    assertThat(CsvCells.escape("-2")).isEqualTo("'-2");
    assertThat(CsvCells.escape("@SUM(A1)")).isEqualTo("'@SUM(A1)");
    assertThat(CsvCells.escape("\t=1")).isEqualTo("'\t=1");
    assertThat(CsvCells.escape("\r=1")).isEqualTo("\"'\r=1\"");
    assertThat(CsvCells.escape("=HYPERLINK(\"x\",\"y\")"))
        .isEqualTo("\"'=HYPERLINK(\"\"x\"\",\"\"y\"\")\"");
    assertThat(CsvCells.escape("a=b")).isEqualTo("a=b");
    assertThat(CsvCells.escape(-2)).isEqualTo("-2");
  }

  @Test
  void appendsRowJoinedByCommaAndTerminatedByNewline() {
    StringBuilder sb = new StringBuilder();
    CsvCells.appendRow(sb, "a", "b,c", 42);
    assertThat(sb.toString()).isEqualTo("a,\"b,c\",42\n");
  }
}
