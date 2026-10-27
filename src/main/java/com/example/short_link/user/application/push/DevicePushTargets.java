package com.example.short_link.user.application.push;

import com.example.short_link.user.domain.DeviceTarget;
import com.example.short_link.user.domain.repository.DeviceTokenRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DevicePushTargets {

  private final DeviceTokenRepository deviceTokens;

  public List<DeviceTarget> forUser(Long userId, Instant now) {
    return deviceTokens.targetsForUser(userId, now);
  }

  public List<DeviceTarget> forUsers(Collection<Long> userIds, Instant now) {
    return deviceTokens.targetsForUsers(userIds, now);
  }

  public void forget(String token) {
    deviceTokens.deleteByToken(token);
  }

  public void recordTopic(String token, String topic) {
    deviceTokens.updateTopic(token, topic);
  }
}
