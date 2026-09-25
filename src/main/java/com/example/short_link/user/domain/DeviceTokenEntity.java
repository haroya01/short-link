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

/** 같은 APNs 토큰으로 다른 계정이 로그인하면 오발송 방지를 위해 소유자를 바꾼다. 로그아웃·탈퇴·BadDeviceToken 응답 시 삭제한다. */
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

  @Column(length = 100)
  private String topic;

  public DeviceTokenEntity(Long userId, String token, String platform, String topic) {
    this.userId = userId;
    this.token = token;
    this.platform = platform;
    this.topic = topic;
  }

  public void reassign(Long newUserId, String topic) {
    this.userId = newUserId;
    if (topic != null) this.topic = topic;
  }
}
