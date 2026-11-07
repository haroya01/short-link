package com.example.short_link.link.stats.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.common.post.PostTitleReader;
import com.example.short_link.link.application.dto.LinkStats;
import com.example.short_link.link.classifier.application.ReferrerChannelClassifier;
import com.example.short_link.link.destination.domain.repository.LinkDestinationRepository;
import com.example.short_link.link.domain.LinkId;
import com.example.short_link.link.stats.domain.projection.ClickProjections.ReferrerClickRow;
import com.example.short_link.link.stats.domain.projection.ClickProjections.ReferrerHostClickRow;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LinkStatsDimensionBreakdownsReaderTest {

  private final ClickDimensionReader clicks = mock(ClickDimensionReader.class);
  private final LinkStatsDimensionBreakdownsReader reader =
      new LinkStatsDimensionBreakdownsReader(
          clicks,
          new ReferrerChannelClassifier(),
          mock(LinkDestinationRepository.class),
          mock(PostTitleReader.class));

  @Test
  void channelTotalsCountEveryHostWhileOnlyTheTopFiftyReferrersAreRead() {
    when(clicks.findTopReferrerClicks(7L, 50))
        .thenReturn(List.of(referrer("https://www.google.com/search", 3L)));
    List<ReferrerHostClickRow> hosts = new ArrayList<>();
    hosts.add(host("www.google.com", 3L));
    for (int i = 0; i < 60; i++) {
      hosts.add(host("p" + i + ".instagram.com", 1L));
    }
    when(clicks.findAllReferrerHostClicks(7L)).thenReturn(hosts);

    LinkStatsDimensionBreakdownsReader.ChannelBreakdowns breakdowns =
        reader.channels(new LinkId(7L), 4L);

    assertThat(breakdowns.referrers()).hasSize(1);
    assertThat(breakdowns.referrerHosts()).hasSize(50).first().isEqualTo(host("www.google.com"));
    assertThat(breakdowns.channels())
        .extracting(LinkStats.ChannelClick::channel, LinkStats.ChannelClick::count)
        .containsExactly(tuple("social", 60L), tuple("direct", 4L), tuple("search", 3L));
    verify(clicks).findTopReferrerClicks(7L, 50);
    verify(clicks).findAllReferrerHostClicks(7L);
    verifyNoMoreInteractions(clicks);
  }

  private static LinkStats.ReferrerHostClick host(String name) {
    return new LinkStats.ReferrerHostClick(name, 3L);
  }

  private static ReferrerClickRow referrer(String referrer, long count) {
    return new ReferrerClickRow() {
      @Override
      public String getReferrer() {
        return referrer;
      }

      @Override
      public Long getCount() {
        return count;
      }
    };
  }

  private static ReferrerHostClickRow host(String host, long count) {
    return new ReferrerHostClickRow() {
      @Override
      public String getHost() {
        return host;
      }

      @Override
      public Long getCount() {
        return count;
      }
    };
  }
}
