package com.example.short_link.blogimport.application.read;

import com.example.short_link.blogimport.domain.ImportSourceEntity;
import java.time.Instant;

public record ImportSourceView(
    Long id,
    String platform,
    String blogUrl,
    String blogTitle,
    String status,
    String verificationCode,
    boolean fullContent,
    long importedCount,
    Instant lastSyncedAt,
    String lastError,
    boolean announceNewPosts,
    boolean showInDiscover,
    Progress progress) {

  public record Progress(int total, int done, int failed) {}

  public static ImportSourceView of(ImportSourceEntity source, long importedCount) {
    Progress progress =
        source.getProgressTotal() == null
            ? null
            : new Progress(
                source.getProgressTotal(),
                source.getProgressDone() == null ? 0 : source.getProgressDone(),
                source.getProgressFailed() == null ? 0 : source.getProgressFailed());
    return new ImportSourceView(
        source.getId(),
        source.getPlatform().name(),
        source.getBlogUrl(),
        source.getBlogTitle(),
        source.getStatus().name(),
        source.getVerificationCode(),
        source.isFullContent(),
        importedCount,
        source.getLastSyncedAt(),
        source.getLastError(),
        source.isAnnounceNewPosts(),
        source.isShowInDiscover(),
        progress);
  }
}
