package com.example.short_link.federation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.common.event.RemoteDomainBlockedEvent;
import com.example.short_link.federation.domain.UserDomainBlockEntity;
import com.example.short_link.federation.domain.repository.UserDomainBlockRepository;
import com.example.short_link.federation.exception.FederationErrorCode;
import com.example.short_link.federation.exception.FederationException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class DomainBlocksTest {

  @Mock private UserDomainBlockRepository blocks;
  @Mock private RemoteFollowing following;
  @Mock private FederationFollowers followers;
  @Mock private ApplicationEventPublisher events;

  private DomainBlocks service() {
    return new DomainBlocks(
        blocks,
        following,
        followers,
        new FederationUrls(new FederationProperties("https://kurl.me", "https://blog.kurl.me")),
        events);
  }

  @Test
  void blockingAServerEndsFollowsBothWaysAndClearsItsNotices() {
    when(blocks.find(7L, "mastodon.social")).thenReturn(Optional.empty());
    when(blocks.save(any())).thenAnswer(inv -> inv.getArgument(0));

    DomainBlocks.View view = service().block(7L, "  @Mastodon.Social ");

    assertThat(view.domain()).isEqualTo("mastodon.social");
    verify(following).leaveDomain(7L, "mastodon.social");
    verify(followers).rejectDomain(7L, "mastodon.social");
    verify(events).publishEvent(new RemoteDomainBlockedEvent(7L, "mastodon.social"));
  }

  @Test
  void blockingTheSameServerAgainKeepsOneRow() {
    when(blocks.find(7L, "mastodon.social"))
        .thenReturn(Optional.of(new UserDomainBlockEntity(7L, "mastodon.social")));

    service().block(7L, "mastodon.social");

    verify(blocks, never()).save(any());
    verify(following).leaveDomain(7L, "mastodon.social");
  }

  @Test
  void aServerIsADomainAndNeverThisOne() {
    for (String bad :
        List.of("", "mastodon", "https://mastodon.social", "a b.example", "kurl.me")) {
      assertThatThrownBy(() -> service().block(7L, bad))
          .isInstanceOfSatisfying(
              FederationException.class,
              e -> assertThat(e.errorCode()).isEqualTo(FederationErrorCode.REMOTE_DOMAIN_INVALID));
    }
    when(blocks.save(any())).thenAnswer(inv -> inv.getArgument(0));
    assertThat(service().block(7L, "social.example:8443").domain())
        .isEqualTo("social.example:8443");
  }

  @Test
  void unblockingForgetsTheServer() {
    service().unblock(7L, "Mastodon.Social");

    verify(blocks).delete(7L, "mastodon.social");
    verifyNoInteractions(following, followers, events);
  }

  @Test
  void theListIsTheMembersServers() {
    when(blocks.list(7L)).thenReturn(List.of(new UserDomainBlockEntity(7L, "a.example")));

    assertThat(service().list(7L))
        .extracting(DomainBlocks.View::domain)
        .containsExactly("a.example");
  }
}
