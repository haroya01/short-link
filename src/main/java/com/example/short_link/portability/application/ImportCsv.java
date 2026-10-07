package com.example.short_link.portability.application;

import java.util.ArrayList;
import java.util.List;

// RFC 4180, as Mastodon writes its exports: fields split by commas, a quoted field may hold commas,
// line breaks and doubled quotes. Blank lines are skipped and a leading byte-order mark ignored.
final class ImportCsv {

  private ImportCsv() {}

  static List<List<String>> parse(String text) {
    String content = text.startsWith("﻿") ? text.substring(1) : text;
    List<List<String>> rows = new ArrayList<>();
    List<String> row = new ArrayList<>();
    StringBuilder field = new StringBuilder();
    boolean quoted = false;
    for (int i = 0; i < content.length(); i++) {
      char c = content.charAt(i);
      if (quoted) {
        if (c == '"' && i + 1 < content.length() && content.charAt(i + 1) == '"') {
          field.append('"');
          i++;
        } else if (c == '"') {
          quoted = false;
        } else {
          field.append(c);
        }
      } else if (c == '"' && field.isEmpty()) {
        quoted = true;
      } else if (c == ',') {
        row.add(field.toString().trim());
        field.setLength(0);
      } else if (c == '\n' || c == '\r') {
        if (c == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') {
          i++;
        }
        row.add(field.toString().trim());
        field.setLength(0);
        addIfFilled(rows, row);
        row = new ArrayList<>();
      } else {
        field.append(c);
      }
    }
    row.add(field.toString().trim());
    addIfFilled(rows, row);
    return rows;
  }

  private static void addIfFilled(List<List<String>> rows, List<String> row) {
    if (row.stream().anyMatch(cell -> !cell.isEmpty())) {
      rows.add(List.copyOf(row));
    }
  }
}
