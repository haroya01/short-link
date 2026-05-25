package com.example.short_link.link.presentation;

import com.example.short_link.link.application.ShortLinkUrlBuilder;
import com.example.short_link.link.application.dto.BulkImportResult;
import com.example.short_link.link.application.dto.BulkImportRow;
import com.example.short_link.link.application.write.ImportLinksFromCsvCommand;
import com.example.short_link.link.application.write.ImportLinksFromCsvUseCase;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/links")
@RequiredArgsConstructor
public class BulkImportController {

  private final ImportLinksFromCsvUseCase useCase;
  private final ShortLinkUrlBuilder urlBuilder;

  @PostMapping(value = "/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<String> bulkImport(
      @AuthenticationPrincipal Long userId, @RequestParam("file") MultipartFile file)
      throws IOException {
    if (file.isEmpty()) {
      return ResponseEntity.badRequest().body("empty file");
    }
    BulkImportResult result =
        useCase.execute(new ImportLinksFromCsvCommand(userId, file.getInputStream()));
    String csv = renderCsv(result);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/csv; charset=utf-8"))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"bulk-result.csv\"")
        .header("X-Bulk-Ok", String.valueOf(result.ok()))
        .header("X-Bulk-Failed", String.valueOf(result.failed()))
        .body(csv);
  }

  private String renderCsv(BulkImportResult result) {
    StringBuilder sb = new StringBuilder(result.rows().size() * 96);
    sb.append("url,custom_code,expires_at,short_code,short_url,error\n");
    for (BulkImportRow row : result.rows()) {
      sb.append(escape(row.url()))
          .append(',')
          .append(escape(row.customCode()))
          .append(',')
          .append(escape(row.expiresAt()))
          .append(',')
          .append(escape(row.shortCode()))
          .append(',')
          .append(row.shortCode() == null ? "" : escape(urlBuilder.build(row.shortCode())))
          .append(',')
          .append(escape(row.error()))
          .append('\n');
    }
    return sb.toString();
  }

  private static String escape(String s) {
    if (s == null) return "";
    if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
      return "\"" + s.replace("\"", "\"\"") + "\"";
    }
    return s;
  }
}
