package com.example.short_link.federation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.event.ServerSuspendedEvent;
import com.example.short_link.federation.domain.FederationDomainBlockEntity;
import com.example.short_link.federation.domain.ServerBlockSeverity;
import com.example.short_link.federation.domain.repository.FederationDomainBlockRepository;
import com.example.short_link.federation.exception.FederationException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ServerBlocksTest {

  @Mock private FederationDomainBlockRepository blocks;
  @Mock private ApplicationEventPublisher events;

  private ServerBlocks service() {
    return new ServerBlocks(
        blocks,
        new FederationUrls(new FederationProperties("https://kurl.me", "https://blog.kurl.me")),
        events);
  }

  @Test
  void limitingAServerKeepsFollowsAndNotices() {
    when(blocks.find("spam.example")).thenReturn(Optional.empty());
    when(blocks.save(any())).thenAnswer(inv -> inv.getArgument(0));

    ServerBlocks.View view = service().block(" @Spam.Example ", ServerBlockSeverity.LIMIT, "  ");

    assertThat(view.domain()).isEqualTo("spam.example");
    assertThat(view.severity()).isEqualTo(ServerBlockSeverity.LIMIT);
    assertThat(view.reason()).isNull();
    verify(blocks, never()).sever(anyString());
    verify(events, never()).publishEvent(any(Object.class));
  }

  @Test
  void suspendingAServerCutsItOffAndClearsItsNotices() {
    when(blocks.find("spam.example")).thenReturn(Optional.empty());
    when(blocks.save(any())).thenAnswer(inv -> inv.getArgument(0));

    ServerBlocks.View view =
        service().block("spam.example", ServerBlockSeverity.SUSPEND, " harassment ");

    assertThat(view.reason()).isEqualTo("harassment");
    verify(blocks).sever("spam.example");
    verify(events).publishEvent(new ServerSuspendedEvent("spam.example"));
  }

  @Test
  void blockingAgainChangesTheOneRow() {
    FederationDomainBlockEntity row =
        new FederationDomainBlockEntity("spam.example", ServerBlockSeverity.LIMIT, null);
    when(blocks.find("spam.example")).thenReturn(Optional.of(row));

    service().block("spam.example", ServerBlockSeverity.SUSPEND, "worse");

    assertThat(row.getSeverity()).isEqualTo(ServerBlockSeverity.SUSPEND);
    assertThat(row.getReason()).isEqualTo("worse");
    verify(blocks, never()).save(any());
  }

  @Test
  void thisServerAndNonsenseAreNotServersToBlock() {
    assertThatThrownBy(() -> service().block("kurl.me", ServerBlockSeverity.SUSPEND, null))
        .isInstanceOf(FederationException.class);
    assertThatThrownBy(() -> service().block("not a domain", ServerBlockSeverity.LIMIT, null))
        .isInstanceOf(FederationException.class);
  }

  @Test
  void listsAndLifts() {
    when(blocks.list())
        .thenReturn(
            List.of(new FederationDomainBlockEntity("a.example", ServerBlockSeverity.LIMIT, "x")));

    assertThat(service().list()).extracting(ServerBlocks.View::domain).containsExactly("a.example");

    service().unblock("A.Example");
    verify(blocks).delete("a.example");
  }
}
