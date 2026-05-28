package com.example.short_link.admin.infrastructure;

import com.example.short_link.admin.application.read.AdminMetricReader;
import com.example.short_link.admin.domain.repository.AdminMetricsRepository.LinkStatRow;
import com.example.short_link.admin.domain.repository.AdminMetricsRepository.StatPage;
import com.example.short_link.admin.domain.repository.AdminMetricsRepository.UserStatRow;
import com.example.short_link.admin.infrastructure.persistence.JpaAdminMetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminMetricReaderAdapter implements AdminMetricReader {

  private final JpaAdminMetricsRepository repository;

  @Override
  public StatPage<UserStatRow> topUsersByLinks(int page, int size) {
    Page<UserStatRow> rows = repository.topUsersByLinks(PageRequest.of(page, size));
    return new StatPage<>(rows.getContent(), rows.getTotalElements());
  }

  @Override
  public StatPage<UserStatRow> topUsersByClicks(int page, int size) {
    Page<UserStatRow> rows = repository.topUsersByClicks(PageRequest.of(page, size));
    return new StatPage<>(rows.getContent(), rows.getTotalElements());
  }

  @Override
  public StatPage<LinkStatRow> topLinksByClicks(int page, int size) {
    Page<LinkStatRow> rows = repository.topLinksByClicks(PageRequest.of(page, size));
    return new StatPage<>(rows.getContent(), rows.getTotalElements());
  }
}
