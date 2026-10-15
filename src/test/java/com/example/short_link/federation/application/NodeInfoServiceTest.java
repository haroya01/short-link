package com.example.short_link.federation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.short_link.federation.domain.NodeUsage;
import com.example.short_link.federation.domain.repository.NodeUsageReader;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NodeInfoServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-15T00:00:00Z");

  @Mock private NodeUsageReader reader;

  @Test
  void usageIsReadAsOfNow() {
    when(reader.read(NOW)).thenReturn(new NodeUsage(3, 41));

    assertThat(new NodeInfoService(reader, Clock.fixed(NOW, ZoneOffset.UTC)).usage())
        .isEqualTo(new NodeUsage(3, 41));
  }
}
