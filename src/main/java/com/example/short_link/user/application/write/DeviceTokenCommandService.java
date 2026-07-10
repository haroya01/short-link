package com.example.short_link.user.application.write;

import com.example.short_link.user.domain.DeviceTokenEntity;
import com.example.short_link.user.domain.repository.DeviceTokenRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 푸시 디바이스 등록 — 토큰 콜백마다 upsert(소유자 갈아끼움 포함), 로그아웃 때 삭제. */
@Service
@RequiredArgsConstructor
public class DeviceTokenCommandService {

  private final DeviceTokenRepository deviceTokens;
  private final UserRepository userRepository;

  /** 같은 기기에 다른 계정이 로그인하면 소유자를 갈아끼운다 — 이전 계정으로의 오발송 방지. */
  @Transactional
  public void register(Long userId, String token, String platform) {
    deviceTokens
        .findByToken(token)
        .ifPresentOrElse(
            existing -> existing.reassign(userId),
            () -> deviceTokens.save(new DeviceTokenEntity(userId, token, platform)));
    // 이 기기의 언어를 사용자 로케일로 — 서버조합 푸시를 그 언어로 낸다(Accept-Language).
    userRepository
        .findById(userId)
        .ifPresent(u -> u.updateLocale(LocaleContextHolder.getLocale().getLanguage()));
  }

  /** 자기 소유 토큰만 해지한다 — 남의 APNs 토큰을 알아도 못 지우게(푸시 차단 방지). */
  @Transactional
  public void unregister(Long userId, String token) {
    deviceTokens
        .findByToken(token)
        .filter(existing -> existing.getUserId().equals(userId))
        .ifPresent(existing -> deviceTokens.deleteByToken(token));
  }
}
