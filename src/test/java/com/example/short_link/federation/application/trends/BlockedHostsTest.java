package com.example.short_link.federation.application.trends;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.federation.domain.ServerBlockSeverity;
import com.example.short_link.federation.domain.repository.FederationDomainBlockRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BlockedHostsTest {

  @Test
  void eachHostIsAskedOfTheServerBlocksOnce() {
    FederationDomainBlockRepository serverBlocks = mock(FederationDomainBlockRepository.class);
    when(serverBlocks.severityFor("bad.example"))
        .thenReturn(Optional.of(ServerBlockSeverity.SUSPEND));
    when(serverBlocks.severityFor("good.example")).thenReturn(Optional.empty());
    BlockedHosts blocked = new BlockedHosts(serverBlocks);

    assertThat(blocked.contains("bad.example")).isTrue();
    assertThat(blocked.contains("good.example")).isFalse();
    assertThat(blocked.contains("bad.example")).isTrue();
    assertThat(blocked.contains("good.example")).isFalse();
    verify(serverBlocks, times(1)).severityFor("bad.example");
    verify(serverBlocks, times(1)).severityFor("good.example");
  }
}
