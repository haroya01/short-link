package com.example.short_link.user.application.write;

import com.example.short_link.user.domain.WebPushSubscriptionEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.domain.repository.WebPushSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 웹푸시 구독 등록 — 구독 콜백마다 upsert(소유자 갈아끼움 포함), 구독해제 때 삭제. */
@Service
@RequiredArgsConstructor
public class WebPushSubscriptionCommandService {

  private final WebPushSubscriptionRepository subscriptions;
  private final UserRepository userRepository;

  /** 같은 브라우저(endpoint)가 다른 계정으로 재구독하면 소유자를 갈아끼운다 — 오발송 방지. */
  @Transactional
  public void subscribe(Long userId, String endpoint, String p256dh, String auth) {
    subscriptions
        .findByEndpoint(endpoint)
        .ifPresentOrElse(
            existing -> existing.reassign(userId),
            () ->
                subscriptions.save(new WebPushSubscriptionEntity(userId, endpoint, p256dh, auth)));
    // 이 브라우저의 언어를 사용자 로케일로 — 서버조합 푸시를 그 언어로 낸다(Accept-Language).
    userRepository
        .findById(userId)
        .ifPresent(u -> u.updateLocale(LocaleContextHolder.getLocale().getLanguage()));
  }

  /** 소유자 검증 없이 endpoint 만으로 지운다 — endpoint 를 아는 쪽이 그 브라우저다. */
  @Transactional
  public void unsubscribe(String endpoint) {
    subscriptions.deleteByEndpoint(endpoint);
  }
}
