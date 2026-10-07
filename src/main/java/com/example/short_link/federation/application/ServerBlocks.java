package com.example.short_link.federation.application;

import com.example.short_link.common.event.ServerSuspendedEvent;
import com.example.short_link.federation.domain.FederationDomainBlockEntity;
import com.example.short_link.federation.domain.ServerBlockSeverity;
import com.example.short_link.federation.domain.repository.FederationDomainBlockRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Moderators block a whole server, as on Mastodon. A limited server's notes stay out of what
// members discover (trending, tags) unless they follow the account, and its notices reach only
// its followers. A suspended server is cut off: nothing is taken from it or sent to it, follows
// both ways end, its notices go, and its notes are hidden wherever notes are read — hidden, not
// purged, so lifting the block brings them back.
@Service
@RequiredArgsConstructor
public class ServerBlocks {

  private final FederationDomainBlockRepository blocks;
  private final FederationUrls urls;
  private final ApplicationEventPublisher events;

  public record View(
      String domain, ServerBlockSeverity severity, String reason, Instant createdAt) {

    static View of(FederationDomainBlockEntity row) {
      return new View(row.getDomain(), row.getSeverity(), row.getReason(), row.getCreatedAt());
    }
  }

  @Transactional(readOnly = true)
  public List<View> list() {
    return blocks.list().stream().map(View::of).toList();
  }

  @Transactional
  public View block(String raw, ServerBlockSeverity severity, String reason) {
    String domain = DomainBlocks.normalize(raw, urls.domain());
    String note = reason == null || reason.isBlank() ? null : reason.strip();
    FederationDomainBlockEntity row =
        blocks
            .find(domain)
            .map(
                existing -> {
                  existing.change(severity, note);
                  return existing;
                })
            .orElseGet(() -> blocks.save(new FederationDomainBlockEntity(domain, severity, note)));
    if (severity == ServerBlockSeverity.SUSPEND) {
      blocks.sever(domain);
      events.publishEvent(new ServerSuspendedEvent(domain));
    }
    return View.of(row);
  }

  @Transactional
  public void unblock(String raw) {
    blocks.delete(DomainBlocks.normalize(raw, urls.domain()));
  }
}
