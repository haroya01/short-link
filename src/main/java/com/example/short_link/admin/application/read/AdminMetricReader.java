package com.example.short_link.admin.application.read;

import com.example.short_link.admin.domain.repository.AdminMetricsRepository.LinkStatRow;
import com.example.short_link.admin.domain.repository.AdminMetricsRepository.StatPage;
import com.example.short_link.admin.domain.repository.AdminMetricsRepository.UserStatRow;

public interface AdminMetricReader {
  StatPage<UserStatRow> topUsersByLinks(int page, int size);

  StatPage<UserStatRow> topUsersByClicks(int page, int size);

  StatPage<LinkStatRow> topLinksByClicks(int page, int size);
}
