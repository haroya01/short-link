package com.example.short_link.user.application.write;

import com.example.short_link.user.application.properties.JwtProperties;
import com.example.short_link.user.domain.DeviceTokenEntity;
import com.example.short_link.user.domain.repository.DeviceTokenRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeviceTokenCommandService {

  private final DeviceTokenRepository deviceTokens;
  private final UserRepository userRepository;
  private final JwtProperties jwtProperties;
  private final Clock clock;

  // 같은 기기에 다른 계정이 로그인하면 소유자를 갈아끼운다 — 이전 계정으로의 오발송 방지.
  // 토큰은 등록한 로그인 세션에 묶인다 — 그 세션이 끝나면(로그아웃·만료) 이 기기로 보내지 않는다.
  @Transactional
  public void register(Long userId, String token, String platform, String topic, String sessionId) {
    DeviceTokenEntity device =
        deviceTokens
            .findByToken(token)
            .map(
                existing -> {
                  existing.reassign(userId, topic);
                  return existing;
                })
            .orElseGet(
                () -> deviceTokens.save(new DeviceTokenEntity(userId, token, platform, topic)));
    device.bindTo(sessionId, clock.instant().plus(jwtProperties.refreshTtl()));
    // iOS 앱은 기기 등록 때 Accept-Language 를 보내야 한다. 서버가 조합하는 푸시가 이 언어로 나간다.
    userRepository
        .findById(userId)
        .ifPresent(u -> u.updateLocale(LocaleContextHolder.getLocale().getLanguage()));
  }

  // 자기 소유 토큰만 해지한다 — 남의 APNs 토큰을 알아도 못 지우게(푸시 차단 방지).
  @Transactional
  public void unregister(Long userId, String token) {
    deviceTokens
        .findByToken(token)
        .filter(existing -> existing.getUserId().equals(userId))
        .ifPresent(existing -> deviceTokens.deleteByToken(token));
  }
}
