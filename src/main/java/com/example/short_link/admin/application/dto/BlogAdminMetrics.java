package com.example.short_link.admin.application.dto;

import java.util.List;

// com.example.short_link.admin.config.AdminCacheConfig의 역직렬화 허용 범위인 admin.application 패키지에 둔다.
public record BlogAdminMetrics(
    long totalPosts,
    long totalReads,
    long activeAuthors,
    long openReports,
    List<TopPost> topPosts) {

  public record TopPost(long id, String title, String authorHandle, long reads, String url) {}
}
