package com.example.short_link.user.domain;

import com.example.short_link.common.jpa.BaseCreatedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * APNs 디바이스 토큰. token 이 유니크 — 같은 기기에 다른 계정이 로그인하면 소유자를 갈아끼운다(이전 계정으로의 오발송 방지).
 * 로그아웃·탈퇴·BadDeviceToken 응답 시 삭제.
 */
@Entity
@Table(name = "device_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeviceTokenEntity extends BaseCreatedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(nullable = false, length = 200, unique = true)
  private String token;

  @Column(nullable = false, length = 16)
  private String platform;

  public DeviceTokenEntity(Long userId, String token, String platform) {
    this.userId = userId;
    this.token = token;
    this.platform = platform;
  }

  public void reassign(Long newUserId) {
    this.userId = newUserId;
  }
}
