package com.example.short_link.profile.email;

import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/email-leads")
@RequiredArgsConstructor
public class MyEmailLeadController {

  private final EmailLeadService service;

  @GetMapping
  public Page list(
      @AuthenticationPrincipal Long userId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    long total = service.count(userId);
    List<LeadResponse> items =
        service.list(userId, page, size).stream()
            .map(l -> new LeadResponse(l.getId(), l.getBlockId(), l.getEmail(), l.getSubmittedAt()))
            .toList();
    return new Page(items, total);
  }

  @GetMapping(value = "/export.csv", produces = "text/csv")
  public ResponseEntity<String> export(@AuthenticationPrincipal Long userId) {
    StringBuilder csv = new StringBuilder("email,block_id,submitted_at\n");
    // Cap export at the same page-size ceiling so the request stays bounded; the dashboard can
    // page if a creator collects more, and we'd switch to a streaming export then.
    for (EmailLeadEntity l : service.list(userId, 0, 500)) {
      csv.append(escape(l.getEmail()))
          .append(',')
          .append(l.getBlockId())
          .append(',')
          .append(l.getSubmittedAt())
          .append('\n');
    }
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/csv; charset=utf-8"))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"email-leads.csv\"")
        .body(csv.toString());
  }

  @DeleteMapping("/{id}")
  public void delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
    service.delete(userId, id);
  }

  private static String escape(String value) {
    if (value == null) return "";
    if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
      return "\"" + value.replace("\"", "\"\"") + "\"";
    }
    return value;
  }

  public record LeadResponse(Long id, Long blockId, String email, Instant submittedAt) {}

  public record Page(List<LeadResponse> items, long total) {}
}
