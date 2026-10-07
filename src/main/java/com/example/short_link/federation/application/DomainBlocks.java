package com.example.short_link.federation.application;

import com.example.short_link.common.event.RemoteDomainBlockedEvent;
import com.example.short_link.federation.domain.UserDomainBlockEntity;
import com.example.short_link.federation.domain.repository.UserDomainBlockRepository;
import com.example.short_link.federation.exception.FederationErrorCode;
import com.example.short_link.federation.exception.FederationException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// A member blocks a whole server, as on Mastodon: their follows there end, followers there are
// rejected, notices from there go, and its notes and notices stay out from then on.
@Service
@RequiredArgsConstructor
public class DomainBlocks {

  private static final Pattern DOMAIN =
      Pattern.compile(
          "[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+(:[0-9]{1,5})?");

  private final UserDomainBlockRepository blocks;
  private final RemoteFollowing following;
  private final FederationFollowers followers;
  private final FederationUrls urls;
  private final ApplicationEventPublisher events;

  public record View(String domain, Instant createdAt) {

    static View of(UserDomainBlockEntity row) {
      return new View(row.getDomain(), row.getCreatedAt());
    }
  }

  @Transactional(readOnly = true)
  public List<View> list(Long userId) {
    return blocks.list(userId).stream().map(View::of).toList();
  }

  @Transactional
  public View block(Long userId, String raw) {
    String domain = normalize(raw);
    UserDomainBlockEntity row =
        blocks
            .find(userId, domain)
            .orElseGet(() -> blocks.save(new UserDomainBlockEntity(userId, domain)));
    following.leaveDomain(userId, domain);
    followers.rejectDomain(userId, domain);
    events.publishEvent(new RemoteDomainBlockedEvent(userId, domain));
    return View.of(row);
  }

  @Transactional
  public void unblock(Long userId, String raw) {
    blocks.delete(userId, normalize(raw));
  }

  private String normalize(String raw) {
    return normalize(raw, urls.domain());
  }

  // A server as people type it (any case, a leading @), never this one.
  static String normalize(String raw, String ownDomain) {
    String domain = raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
    if (domain.startsWith("@")) {
      domain = domain.substring(1);
    }
    if (!DOMAIN.matcher(domain).matches() || domain.equals(ownDomain)) {
      throw new FederationException(FederationErrorCode.REMOTE_DOMAIN_INVALID);
    }
    return domain;
  }
}
