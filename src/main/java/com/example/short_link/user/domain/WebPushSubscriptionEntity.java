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

// p256dh/auth는 브라우저의 공개키·인증 시크릿이다. 로그아웃·구독 해제·푸시 서비스 404/410 응답 시 삭제한다.
@Entity
@Table(name = "web_push_subscription")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WebPushSubscriptionEntity extends BaseCreatedEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(nullable = false, length = 512, unique = true)
  private String endpoint;

  @Column(nullable = false, length = 255)
  private String p256dh;

  @Column(nullable = false, length = 255)
  private String auth;

  public WebPushSubscriptionEntity(Long userId, String endpoint, String p256dh, String auth) {
    this.userId = userId;
    this.endpoint = endpoint;
    this.p256dh = p256dh;
    this.auth = auth;
  }

  public void reassign(Long newUserId) {
    this.userId = newUserId;
  }
}
