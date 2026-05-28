package com.example.short_link.abuse.application.read;

import com.example.short_link.abuse.domain.AbuseReportStatus;
import com.example.short_link.abuse.domain.repository.AbuseReportRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AbuseReportQueryService {

  private final AbuseReportRepository abuseReportRepository;

  public List<AbuseReportView> listAll() {
    return abuseReportRepository.findAllByOrderByCreatedAtDesc().stream()
        .map(AbuseReportView::from)
        .toList();
  }

  public List<AbuseReportView> listByStatus(AbuseReportStatus status) {
    return abuseReportRepository.findAllByStatusOrderByCreatedAtDesc(status).stream()
        .map(AbuseReportView::from)
        .toList();
  }
}
