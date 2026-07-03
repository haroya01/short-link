package com.example.short_link.abuse.presentation;

import com.example.short_link.abuse.application.read.AbuseReportQueryService;
import com.example.short_link.abuse.application.read.AbuseReportView;
import com.example.short_link.abuse.application.write.ResolveAbuseReportCommand;
import com.example.short_link.abuse.application.write.ResolveAbuseReportUseCase;
import com.example.short_link.abuse.domain.AbuseReportStatus;
import com.example.short_link.abuse.presentation.request.ResolveAbuseReportRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/abuse-reports")
@RequiredArgsConstructor
public class AdminAbuseReportController {

  private final AbuseReportQueryService queryService;
  private final ResolveAbuseReportUseCase resolveAbuseReport;

  @GetMapping
  public List<AbuseReportView> list(
      @RequestParam(value = "status", required = false) String status) {
    if (status == null || status.isBlank()) {
      return queryService.listAll();
    }
    return queryService.listByStatus(AbuseReportStatus.valueOf(status.toUpperCase()));
  }

  @PostMapping("/{id}/resolve")
  public AbuseReportView resolve(
      @PathVariable Long id, @Valid @RequestBody ResolveAbuseReportRequest request) {
    return queryService.enrich(
        resolveAbuseReport.execute(
            new ResolveAbuseReportCommand(
                id,
                ResolveAbuseReportCommand.Resolution.valueOf(request.resolution().toUpperCase()),
                request.adminNote())));
  }
}
