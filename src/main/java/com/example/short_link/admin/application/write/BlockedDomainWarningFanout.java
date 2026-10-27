package com.example.short_link.admin.application.write;

import com.example.short_link.admin.application.helper.BlockedDomainNormalizer;
import com.example.short_link.admin.application.helper.WarningCopy;
import com.example.short_link.link.application.dto.LinkRef;
import com.example.short_link.link.application.read.LinkLookupQueryService;
import com.example.short_link.notification.application.link.LinkNotificationDispatcher;
import com.example.short_link.notification.domain.LinkNotificationType;
import com.example.short_link.user.application.read.UserSummary;
import com.example.short_link.user.application.read.UserSummaryReader;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// 차단 커밋 후 트랜잭션 밖에서 약관 위반 경고를 소유자당 한 번 발송한다.
@Slf4j
@Component
@RequiredArgsConstructor
public class BlockedDomainWarningFanout {

  private final LinkLookupQueryService links;
  private final UserSummaryReader users;
  private final LinkNotificationDispatcher dispatcher;

  public int execute(String domain) {
    Map<Long, List<LinkRef>> byOwner =
        links.findWithUrlContaining(domain).stream()
            .filter(l -> l.userId() != null)
            .filter(l -> isCovered(BlockedDomainNormalizer.hostOf(l.originalUrl()), domain))
            .collect(Collectors.groupingBy(LinkRef::userId));
    if (byOwner.isEmpty()) {
      return 0;
    }
    Map<Long, String> locales =
        users.findAll(byOwner.keySet()).stream()
            .collect(Collectors.toMap(UserSummary::id, UserSummary::locale));
    byOwner.forEach(
        (userId, owned) -> {
          WarningCopy.Copy copy = WarningCopy.domainBlocked(locales.get(userId), domain);
          String shortCode = owned.size() == 1 ? owned.get(0).shortCode().value() : null;
          try {
            dispatcher.dispatch(
                userId, LinkNotificationType.WARNING, shortCode, copy.subtitle(), copy.body());
          } catch (Exception e) {
            log.warn("domain-block warning skipped for user {}: {}", userId, e.toString());
          }
        });
    return byOwner.size();
  }

  private static boolean isCovered(String host, String domain) {
    return host != null && (host.equals(domain) || host.endsWith("." + domain));
  }
}
