package com.example.short_link.link.presentation.stats;

import com.example.short_link.link.application.LinkExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/links")
@RequiredArgsConstructor
public class LinkExportController {

  private static final MediaType CSV_UTF8 = MediaType.parseMediaType("text/csv;charset=UTF-8");

  private final LinkExportService service;

  @GetMapping("/{shortCode}/events.csv")
  public ResponseEntity<String> eventsCsv(
      @AuthenticationPrincipal Long userId, @PathVariable String shortCode) {
    String body = service.exportEventsCsv(userId, shortCode);
    return ResponseEntity.ok()
        .contentType(CSV_UTF8)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"" + shortCode + "-events.csv\"")
        .body(body);
  }

  @GetMapping("/{shortCode}/stats.csv")
  public ResponseEntity<String> statsCsv(
      @AuthenticationPrincipal Long userId,
      @PathVariable String shortCode,
      @RequestParam(required = false, defaultValue = "daily") String dimension) {
    String body = service.exportStatsCsv(userId, shortCode, dimension);
    return ResponseEntity.ok()
        .contentType(CSV_UTF8)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"" + shortCode + "-" + dimension + ".csv\"")
        .body(body);
  }
}
