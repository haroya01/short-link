package com.example.short_link.user.application.write;

import com.example.short_link.user.domain.DeviceTokenEntity;
import com.example.short_link.user.domain.repository.DeviceTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 푸시 디바이스 등록 — 토큰 콜백마다 upsert(소유자 갈아끼움 포함), 로그아웃 때 삭제. */
@Service
@RequiredArgsConstructor
public class DeviceTokenCommandService {

  private final DeviceTokenRepository deviceTokens;

  /** 같은 기기에 다른 계정이 로그인하면 소유자를 갈아끼운다 — 이전 계정으로의 오발송 방지. */
  @Transactional
  public void register(Long userId, String token, String platform) {
    deviceTokens
        .findByToken(token)
        .ifPresentOrElse(
            existing -> existing.reassign(userId),
            () -> deviceTokens.save(new DeviceTokenEntity(userId, token, platform)));
  }

  /** 소유자 검증 없이 토큰만으로 지운다 — 토큰을 아는 쪽이 그 기기다(로그아웃 직후 호출). */
  @Transactional
  public void unregister(String token) {
    deviceTokens.deleteByToken(token);
  }
}
