package com.example.short_link.federation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.federation.domain.RemoteAccountMatch;
import com.example.short_link.federation.domain.repository.RemoteActorRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RemoteAccountSearchTest {

  @Mock private RemoteActorRepository actors;

  private RemoteAccountSearch search() {
    return new RemoteAccountSearch(actors);
  }

  @Test
  void aQueryShorterThanTwoCharactersAsksNothing() {
    for (String q : new String[] {null, "", "   ", " a ", "@a", "@", "가"}) {
      assertThat(search().search(7L, q, 10)).isEmpty();
    }
    verifyNoInteractions(actors);
  }

  @Test
  void aLeadingAtIsDroppedAndTheTermIsTrimmed() {
    when(actors.search(7L, "alice@mast", 10)).thenReturn(List.of());

    assertThat(search().search(7L, "  @alice@mast ", 10)).isEmpty();
    verify(actors).search(7L, "alice@mast", 10);
  }

  @Test
  void twoCharactersAreEnoughEvenOutsideAscii() {
    when(actors.search(null, "유키", 10)).thenReturn(List.of());

    search().search(null, "유키", 10);
    verify(actors).search(null, "유키", 10);
  }

  @Test
  void theLimitIsKeptBetweenOneAndTwenty() {
    when(actors.search(7L, "al", 1)).thenReturn(List.of());
    when(actors.search(7L, "al", 20)).thenReturn(List.of());

    search().search(7L, "al", 0);
    search().search(7L, "al", 500);
    verify(actors).search(7L, "al", 1);
    verify(actors).search(7L, "al", 20);
  }

  @Test
  void matchesBecomeAccountViewsWithTheViewersFollow() {
    when(actors.search(7L, "alice", 10))
        .thenReturn(
            List.of(
                new RemoteAccountMatch(
                    42L,
                    "alice",
                    "mastodon.example",
                    "Alice",
                    "https://mastodon.example/a.png",
                    "https://mastodon.example/@alice",
                    true,
                    false),
                new RemoteAccountMatch(
                    43L,
                    "alicia",
                    "social.example",
                    null,
                    null,
                    "https://social.example/users/alicia",
                    false,
                    true)));

    assertThat(search().search(7L, "alice", 10))
        .containsExactly(
            new RemoteAccountView(
                42L,
                "alice@mastodon.example",
                "alice",
                "mastodon.example",
                "Alice",
                "https://mastodon.example/a.png",
                "https://mastodon.example/@alice",
                true,
                false,
                false),
            new RemoteAccountView(
                43L,
                "alicia@social.example",
                "alicia",
                "social.example",
                null,
                null,
                "https://social.example/users/alicia",
                false,
                true,
                false));
  }
}
