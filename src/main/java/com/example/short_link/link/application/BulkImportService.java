package com.example.short_link.link.application;

import com.example.short_link.link.application.dto.LinkCreated;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Imports a CSV of URLs to be shortened in one go. Header row is optional. Recognized columns
 * (case-insensitive): {@code url} (required), {@code custom_code}, {@code expires_at} (ISO-8601).
 * Rows that fail validation or shortening are returned with an error reason; the rest of the batch
 * still goes through.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BulkImportService {

  public static final int MAX_ROWS = 100;

  private final LinkCreationService creationService;
  private final MeterRegistry meterRegistry;

  public BulkImportResult importCsv(Long userId, InputStream csv) throws IOException {
    List<BulkImportRow> rows = parse(csv);
    if (rows.size() > MAX_ROWS) {
      throw new LinkException(LinkErrorCode.BULK_IMPORT_TOO_LARGE, rows.size(), MAX_ROWS)
          .with("rows", rows.size())
          .with("limit", MAX_ROWS);
    }

    int ok = 0;
    int failed = 0;
    List<BulkImportRow> results = new ArrayList<>(rows.size());
    for (BulkImportRow row : rows) {
      try {
        if (row.url() == null || row.url().isBlank()) {
          throw new IllegalArgumentException("url required");
        }
        if (!row.url().trim().matches("^https?://.+")) {
          throw new IllegalArgumentException("url must start with http:// or https://");
        }
        Instant expires = parseInstant(row.expiresAt());
        LinkCreated created =
            creationService.create(
                row.url().trim(), userId, blankToNull(row.customCode()), expires);
        results.add(row.withResult(created.shortCode(), null));
        ok++;
      } catch (RuntimeException e) {
        results.add(
            row.withResult(
                null,
                e instanceof com.example.short_link.link.exception.LinkException le
                    ? le.errorCode().name() + ": " + e.getMessage()
                    : e.getClass().getSimpleName() + ": " + e.getMessage()));
        failed++;
      }
    }
    meterRegistry.counter("bulk_import.rows", "result", "ok").increment(ok);
    meterRegistry.counter("bulk_import.rows", "result", "failed").increment(failed);
    return new BulkImportResult(ok, failed, results);
  }

  private List<BulkImportRow> parse(InputStream csv) throws IOException {
    List<BulkImportRow> rows = new ArrayList<>();
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(csv, StandardCharsets.UTF_8))) {
      String line;
      Header header = null;
      while ((line = reader.readLine()) != null) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) continue;
        String[] cols = splitCsv(trimmed);
        if (header == null) {
          header = Header.detect(cols);
          if (header.hasHeader()) continue;
        }
        rows.add(header.toRow(cols));
        if (rows.size() > MAX_ROWS) break;
      }
    }
    return rows;
  }

  private static Instant parseInstant(String raw) {
    if (raw == null || raw.isBlank()) return null;
    try {
      return Instant.parse(raw.trim());
    } catch (DateTimeParseException e) {
      throw new IllegalArgumentException("expires_at must be ISO-8601");
    }
  }

  private static String blankToNull(String s) {
    if (s == null) return null;
    String t = s.trim();
    return t.isEmpty() ? null : t;
  }

  private static String[] splitCsv(String line) {
    return line.split(",", -1);
  }

  public record BulkImportRow(
      String url, String customCode, String expiresAt, String shortCode, String error) {

    public BulkImportRow withResult(String shortCode, String error) {
      return new BulkImportRow(url, customCode, expiresAt, shortCode, error);
    }
  }

  public record BulkImportResult(int ok, int failed, List<BulkImportRow> rows) {}

  /**
   * Maps column positions. If the first row looks like a header, we use the named columns;
   * otherwise we assume column order: url, custom_code, expires_at.
   */
  private record Header(int urlIdx, int customCodeIdx, int expiresAtIdx, boolean hasHeader) {

    static Header detect(String[] firstRow) {
      int u = -1, c = -1, e = -1;
      boolean looksLikeHeader = false;
      for (int i = 0; i < firstRow.length; i++) {
        String name = firstRow[i].trim().toLowerCase();
        switch (name) {
          case "url" -> {
            u = i;
            looksLikeHeader = true;
          }
          case "custom_code", "customcode" -> {
            c = i;
            looksLikeHeader = true;
          }
          case "expires_at", "expiresat" -> {
            e = i;
            looksLikeHeader = true;
          }
          default -> {}
        }
      }
      if (looksLikeHeader && u >= 0) {
        return new Header(u, c, e, true);
      }
      return new Header(0, 1, 2, false);
    }

    BulkImportRow toRow(String[] cols) {
      return new BulkImportRow(
          get(cols, urlIdx), get(cols, customCodeIdx), get(cols, expiresAtIdx), null, null);
    }

    private static String get(String[] cols, int idx) {
      if (idx < 0 || idx >= cols.length) return null;
      return cols[idx].trim();
    }
  }
}
