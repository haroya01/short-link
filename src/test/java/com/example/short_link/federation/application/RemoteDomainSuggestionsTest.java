package com.example.short_link.federation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.federation.domain.repository.RemoteDomainReader;
import com.example.short_link.federation.domain.repository.RemoteDomainReader.KnownDomain;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RemoteDomainSuggestionsTest {

  private static final FederationUrls URLS =
      new FederationUrls(new FederationProperties("https://kurl.me", "https://blog.kurl.me"));

  @Mock private RemoteDomainReader reader;

  private KnownRemoteDomains known(List<String> trendHosts) {
    return new KnownRemoteDomains(reader, URLS, trendHosts);
  }

  @Test
  void moreKnownAccountsComeFirstThenTheNamedServersInTheirOrderThenByName() {
    when(reader.known(anyCollection(), anyInt()))
        .thenReturn(
            List.of(
                new KnownDomain("zeta.example", 0),
                new KnownDomain("mas.to", 0),
                new KnownDomain("alpha.example", 4),
                new KnownDomain("kurl.me", 9),
                new KnownDomain("misskey.io", 4),
                new KnownDomain("mastodon.social", 0),
                new KnownDomain("trends.example", 0),
                new KnownDomain("beta.example", 4)));

    List<String> ranked = known(List.of(" Trends.Example ", "", "mstdn.jp")).ranked().domains();

    assertThat(ranked)
        .containsExactly(
            "misskey.io",
            "alpha.example",
            "beta.example",
            "mastodon.social",
            "mas.to",
            "trends.example",
            "zeta.example");
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<String>> named = ArgumentCaptor.forClass(List.class);
    verify(reader).known(named.capture(), org.mockito.ArgumentMatchers.eq(KnownRemoteDomains.CAP));
    List<String> expected = new ArrayList<>(KnownRemoteDomains.SEEDS);
    expected.add("trends.example");
    assertThat(named.getValue()).containsExactlyElementsOf(expected);
  }

  @Test
  void whatIsTypedAfterTheLastAtIsTheStartOfTheServer() {
    when(reader.known(anyCollection(), anyInt()))
        .thenReturn(
            List.of(
                new KnownDomain("mastodon.social", 9),
                new KnownDomain("misskey.io", 8),
                new KnownDomain("mstdn.jp", 7),
                new KnownDomain("fedibird.com", 6)));
    RemoteDomainSuggestions suggestions = new RemoteDomainSuggestions(known(List.of()));

    assertThat(suggestions.suggest("@gargron@M", 5))
        .containsExactly("mastodon.social", "misskey.io", "mstdn.jp");
    assertThat(suggestions.suggest(" gargron@mi ", 5)).containsExactly("misskey.io");
    assertThat(suggestions.suggest("mstdn", 5)).containsExactly("mstdn.jp");
    assertThat(suggestions.suggest("@gargron@", 2))
        .containsExactly("mastodon.social", "misskey.io");
    assertThat(suggestions.suggest(null, 0)).containsExactly("mastodon.social");
    assertThat(suggestions.suggest("", 100)).hasSize(4);
    assertThat(suggestions.suggest("@gargron@x", 5)).isEmpty();
  }

  @Test
  void somethingNoServerIsCalledAsksNothing() {
    RemoteDomainSuggestions suggestions = new RemoteDomainSuggestions(known(List.of()));

    assertThat(suggestions.suggest("@a@m a", 5)).isEmpty();
    assertThat(suggestions.suggest("ma/stodon", 5)).isEmpty();
    assertThat(suggestions.suggest("m".repeat(254), 5)).isEmpty();
    verify(reader, never()).known(anyCollection(), anyInt());
  }
}
