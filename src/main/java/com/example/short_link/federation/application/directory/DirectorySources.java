package com.example.short_link.federation.application.directory;

import com.example.short_link.federation.application.DomainBlocks;
import com.example.short_link.federation.application.FederationUrls;
import com.example.short_link.federation.domain.FederationDirectorySourceEntity;
import com.example.short_link.federation.domain.repository.FederationDirectorySourceRepository;
import com.example.short_link.federation.domain.repository.FederationDomainBlockRepository;
import com.example.short_link.federation.exception.FederationErrorCode;
import com.example.short_link.federation.exception.FederationException;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DirectorySources {

  private final FederationDirectorySourceRepository sources;
  private final FederationDomainBlockRepository serverBlocks;
  private final FederationUrls urls;
  private final DirectoryProperties props;

  public record View(
      String host,
      boolean enabled,
      int cap,
      int failures,
      Instant retryAfter,
      Instant lastSyncedAt,
      String lastOutcome) {

    static View of(FederationDirectorySourceEntity row) {
      return new View(
          row.getHost(),
          row.isEnabled(),
          row.getCap(),
          row.getFailures(),
          row.getRetryAfter(),
          row.getLastSyncedAt(),
          row.getLastOutcome());
    }
  }

  public boolean enabled() {
    return props.enabled();
  }

  @Transactional(readOnly = true)
  public List<View> list() {
    return sources.findAllByOrderByHostAsc().stream().map(View::of).toList();
  }

  @Transactional
  public View put(String raw, boolean enabled, Integer cap) {
    if (!props.enabled()) {
      throw new FederationException(FederationErrorCode.DIRECTORY_DISABLED);
    }
    String host = DomainBlocks.normalize(raw, urls.domain());
    if (serverBlocks.severityFor(host).isPresent()) {
      throw new FederationException(FederationErrorCode.DIRECTORY_SOURCE_BLOCKED, host);
    }
    int reads = cap == null ? props.defaultCap() : cap;
    FederationDirectorySourceEntity row =
        sources
            .findById(host)
            .map(
                existing -> {
                  existing.change(enabled, reads);
                  return existing;
                })
            .orElseGet(() -> new FederationDirectorySourceEntity(host, enabled, reads));
    return View.of(sources.save(row));
  }

  @Transactional
  public void delete(String raw) {
    String host = DomainBlocks.normalize(raw, urls.domain());
    sources.delete(
        sources
            .findById(host)
            .orElseThrow(
                () ->
                    new FederationException(FederationErrorCode.DIRECTORY_SOURCE_NOT_FOUND, host)));
  }
}
