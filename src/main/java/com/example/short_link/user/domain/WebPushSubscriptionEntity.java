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
 * 브라우저 웹푸시 구독(APNs 디바이스 토큰의 웹 짝). endpoint 가 유니크 — 같은 브라우저가 재구독하면 소유자를 갈아끼운다(이전 계정으로의 오발송 방지).
 * p256dh·auth 는 브라우저가 준 공개키/인증 시크릿. 로그아웃·구독해제·404/410 응답 시 삭제.
 */
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
