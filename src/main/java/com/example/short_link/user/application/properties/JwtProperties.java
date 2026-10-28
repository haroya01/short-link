package com.example.short_link.user.application.properties;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "short-link.jwt")
public record JwtProperties(
    String privateKey,
    String publicKey,
    Duration accessTtl,
    Duration refreshTtl,
    Duration refreshRotationGrace) {

  public JwtProperties {
    if (privateKey == null) privateKey = "";
    if (publicKey == null) publicKey = "";
    // 쿠키를 나눠 쓰는 탭·서브도메인의 회전 경합을 도용으로 잡지 않기 위한 유예다. 0으로 줄이면 안 된다.
    if (refreshRotationGrace == null) refreshRotationGrace = Duration.ofSeconds(10);
  }
}
