package com.example.short_link.admin.application.write;

import com.example.short_link.admin.application.helper.BlockedDomainNormalizer;
import com.example.short_link.admin.application.helper.WarningCopy;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.notification.application.link.LinkNotificationDispatcher;
import com.example.short_link.notification.domain.LinkNotificationType;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 도메인 차단 직후 그 도메인으로 연결되는 링크의 소유자 전원에게 약관 7조 통지를 자동 발송한다 — 계정당 한 건, 수신자 locale 카피. 커밋된 차단 이후에 호출되므로
 * 트랜잭션 밖(푸시 네트워크 호출 포함).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BlockedDomainWarningFanout {

  private final LinkRepository links;
  private final UserRepository users;
  private final LinkNotificationDispatcher dispatcher;

  /**
   * @return 경고가 발송된 소유자 수
   */
  public int execute(String domain) {
    Map<Long, List<LinkEntity>> byOwner =
        links.findByOriginalUrlContaining(domain).stream()
            .filter(l -> l.getUserId() != null)
            .filter(l -> isCovered(BlockedDomainNormalizer.hostOf(l.getOriginalUrl()), domain))
            .collect(Collectors.groupingBy(LinkEntity::getUserId));
    if (byOwner.isEmpty()) {
      return 0;
    }
    Map<Long, String> locales =
        users.findAllByIdIn(byOwner.keySet()).stream()
            .collect(Collectors.toMap(UserEntity::getId, UserEntity::getLocale));
    byOwner.forEach(
        (userId, owned) -> {
          WarningCopy.Copy copy = WarningCopy.domainBlocked(locales.get(userId), domain);
          String shortCode = owned.size() == 1 ? owned.get(0).getShortCode().value() : null;
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
