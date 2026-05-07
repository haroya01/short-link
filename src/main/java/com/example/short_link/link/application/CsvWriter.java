package com.example.short_link.link.application;

public final class CsvWriter {

  private CsvWriter() {}

  public static String escape(Object value) {
    if (value == null) return "";
    String s = value.toString();
    boolean needsQuotes =
        s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r");
    if (!needsQuotes) return s;
    return "\"" + s.replace("\"", "\"\"") + "\"";
  }

  public static void appendRow(StringBuilder sb, Object... cols) {
    for (int i = 0; i < cols.length; i++) {
      if (i > 0) sb.append(',');
      sb.append(escape(cols[i]));
    }
    sb.append('\n');
  }
}
