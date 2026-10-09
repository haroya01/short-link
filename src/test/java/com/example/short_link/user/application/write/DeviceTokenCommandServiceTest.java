package com.example.short_link.user.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.user.application.properties.JwtProperties;
import com.example.short_link.user.domain.DeviceTokenEntity;
import com.example.short_link.user.domain.repository.DeviceTokenRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeviceTokenCommandServiceTest {

  private static final Instant AT = Instant.parse("2026-10-09T00:00:00Z");

  @Mock private DeviceTokenRepository deviceTokens;

  @Mock(strictness = Mock.Strictness.LENIENT)
  private UserRepository userRepository;

  private DeviceTokenCommandService service;

  @BeforeEach
  void setUp() {
    service =
        new DeviceTokenCommandService(
            deviceTokens,
            userRepository,
            new JwtProperties("", "", Duration.ofMinutes(15), Duration.ofDays(14), null),
            Clock.fixed(AT, ZoneOffset.UTC));
  }

  @Test
  void registerInsertsWhenTokenUnknown() {
    when(deviceTokens.findByToken("tok-1")).thenReturn(Optional.empty());
    when(deviceTokens.save(any())).thenAnswer(call -> call.getArgument(0));

    service.register(1L, "tok-1", "ios", "focustime.kurl.links", null);

    ArgumentCaptor<DeviceTokenEntity> saved = ArgumentCaptor.forClass(DeviceTokenEntity.class);
    verify(deviceTokens).save(saved.capture());
    assertThat(saved.getValue().getUserId()).isEqualTo(1L);
    assertThat(saved.getValue().getToken()).isEqualTo("tok-1");
    assertThat(saved.getValue().getPlatform()).isEqualTo("ios");
    assertThat(saved.getValue().getTopic()).isEqualTo("focustime.kurl.links");
    assertThat(saved.getValue().getSessionId()).isNull();
    assertThat(saved.getValue().getSessionExpiresAt()).isNull();
  }

  @Test
  void aTokenRegisteredInASessionEndsWithIt() {
    when(deviceTokens.findByToken("tok-1")).thenReturn(Optional.empty());
    when(deviceTokens.save(any())).thenAnswer(call -> call.getArgument(0));
    service.register(1L, "tok-1", "ios", null, "s-1");

    ArgumentCaptor<DeviceTokenEntity> saved = ArgumentCaptor.forClass(DeviceTokenEntity.class);
    verify(deviceTokens).save(saved.capture());
    assertThat(saved.getValue().getSessionId()).isEqualTo("s-1");
    assertThat(saved.getValue().getSessionExpiresAt()).isEqualTo(AT.plus(Duration.ofDays(14)));
  }

  @Test
  void registerReassignsKnownTokenToNewAccount() {
    DeviceTokenEntity existing = new DeviceTokenEntity(1L, "tok-1", "ios", null);
    when(deviceTokens.findByToken("tok-1")).thenReturn(Optional.of(existing));

    service.register(2L, "tok-1", "ios", "focustime.kurl", "s-2");

    assertThat(existing.getUserId()).isEqualTo(2L);
    assertThat(existing.getSessionId()).isEqualTo("s-2");
    assertThat(existing.getTopic()).isEqualTo("focustime.kurl");
    verify(deviceTokens, never()).save(any());
  }

  @Test
  void unregisterDeletesWhenCallerOwnsToken() {
    DeviceTokenEntity existing = new DeviceTokenEntity(1L, "tok-1", "ios", null);
    when(deviceTokens.findByToken("tok-1")).thenReturn(Optional.of(existing));

    service.unregister(1L, "tok-1");

    verify(deviceTokens).deleteByToken("tok-1");
  }

  @Test
  void unregisterIgnoresTokenOwnedByAnotherUser() {
    DeviceTokenEntity existing = new DeviceTokenEntity(2L, "tok-1", "ios", null);
    when(deviceTokens.findByToken("tok-1")).thenReturn(Optional.of(existing));

    service.unregister(1L, "tok-1");

    verify(deviceTokens, never()).deleteByToken(any());
  }
}
