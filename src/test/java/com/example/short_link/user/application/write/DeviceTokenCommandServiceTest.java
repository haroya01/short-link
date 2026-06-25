package com.example.short_link.user.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.user.domain.DeviceTokenEntity;
import com.example.short_link.user.domain.repository.DeviceTokenRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeviceTokenCommandServiceTest {

  @Mock private DeviceTokenRepository deviceTokens;
  @InjectMocks private DeviceTokenCommandService service;

  @Test
  void registerInsertsWhenTokenUnknown() {
    when(deviceTokens.findByToken("tok-1")).thenReturn(Optional.empty());

    service.register(1L, "tok-1", "ios");

    ArgumentCaptor<DeviceTokenEntity> saved = ArgumentCaptor.forClass(DeviceTokenEntity.class);
    verify(deviceTokens).save(saved.capture());
    assertThat(saved.getValue().getUserId()).isEqualTo(1L);
    assertThat(saved.getValue().getToken()).isEqualTo("tok-1");
    assertThat(saved.getValue().getPlatform()).isEqualTo("ios");
  }

  @Test
  void registerReassignsKnownTokenToNewAccount() {
    DeviceTokenEntity existing = new DeviceTokenEntity(1L, "tok-1", "ios");
    when(deviceTokens.findByToken("tok-1")).thenReturn(Optional.of(existing));

    service.register(2L, "tok-1", "ios");

    assertThat(existing.getUserId()).isEqualTo(2L);
    verify(deviceTokens, never()).save(any());
  }

  @Test
  void unregisterDeletesWhenCallerOwnsToken() {
    DeviceTokenEntity existing = new DeviceTokenEntity(1L, "tok-1", "ios");
    when(deviceTokens.findByToken("tok-1")).thenReturn(Optional.of(existing));

    service.unregister(1L, "tok-1");

    verify(deviceTokens).deleteByToken("tok-1");
  }

  @Test
  void unregisterIgnoresTokenOwnedByAnotherUser() {
    DeviceTokenEntity existing = new DeviceTokenEntity(2L, "tok-1", "ios");
    when(deviceTokens.findByToken("tok-1")).thenReturn(Optional.of(existing));

    service.unregister(1L, "tok-1");

    verify(deviceTokens, never()).deleteByToken(any());
  }
}
