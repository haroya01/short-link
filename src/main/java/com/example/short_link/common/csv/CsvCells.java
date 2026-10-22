package com.example.short_link.common.csv;

// A text cell starting with one of FORMULA_STARTS is a formula to spreadsheet apps; a leading quote
// keeps it text. Numbers and dates are written as they are.
public final class CsvCells {

  private static final String FORMULA_STARTS = "=+-@\t\r";

  private CsvCells() {}

  public static String escape(Object value) {
    if (value == null) return "";
    String s = value.toString();
    if (value instanceof CharSequence && !s.isEmpty() && FORMULA_STARTS.indexOf(s.charAt(0)) >= 0) {
      s = "'" + s;
    }
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
