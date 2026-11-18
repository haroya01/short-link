package com.example.short_link.note.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.common.note.NoteTopicReader;
import com.example.short_link.note.domain.TrendingLink;
import com.example.short_link.note.domain.TrendingTag;
import com.example.short_link.note.domain.repository.NoteFeedReader;
import com.example.short_link.note.domain.repository.NoteTrendsReader;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NoteTopicProviderTest {

  private static final Instant NOW = Instant.parse("2026-11-18T09:00:00Z");

  @Mock private NoteTrendsReader trends;
  @Mock private NoteFeedReader feeds;

  private NoteTopicProvider provider;

  @BeforeEach
  void provider() {
    provider = new NoteTopicProvider(trends, feeds);
  }

  private static NoteTopicReader.Carrying tag(String name) {
    return new NoteTopicReader.Carrying(name, null);
  }

  private static NoteTopicReader.Carrying link(String url) {
    return new NoteTopicReader.Carrying(null, url);
  }

  @Test
  void thisServersTrendingTagsAndLinksAreHandedOverWithTheirAccounts() {
    when(trends.trendingTags(NOW, 2, 2, 20))
        .thenReturn(List.of(new TrendingTag("소풍", 4, 9, List.of(3L, 6L))));
    when(trends.trendingLinks(NOW, 2, 2, 20))
        .thenReturn(
            List.of(
                new TrendingLink(
                    "https://news.example/a",
                    "A headline",
                    "About it",
                    "https://img.example/a.png",
                    3,
                    5,
                    List.of(2L, 3L))));

    assertThat(provider.trendingTags(NOW, 2, 2, 20))
        .containsExactly(new NoteTopicReader.Tag("소풍", 4));
    assertThat(provider.trendingLinks(NOW, 2, 2, 20))
        .containsExactly(
            new NoteTopicReader.Link(
                "https://news.example/a", "A headline", "https://img.example/a.png", 3));
  }

  @Test
  void keptNotesAreListedByTheTagOrTheAddressAsked() {
    List<NoteTopicReader.Kept> tagged =
        List.of(new NoteTopicReader.Kept(9L, "https://x.example/1", NOW));
    List<NoteTopicReader.Kept> linked = List.of(new NoteTopicReader.Kept(8L, null, NOW));
    when(feeds.taggedBefore("소풍", 7L, 90L, 21)).thenReturn(tagged);
    when(feeds.linkedBefore("https://news.example/a", null, null, 41)).thenReturn(linked);
    when(feeds.taggedAmong("소풍", 7L, List.of("https://x.example/1", "https://x.example/2")))
        .thenReturn(List.of("https://x.example/1"));
    when(feeds.linkedAmong("https://news.example/a", null, List.of("https://x.example/3")))
        .thenReturn(List.of("https://x.example/3"));

    assertThat(provider.kept(tag("소풍"), 7L, 90L, 21)).isEqualTo(tagged);
    assertThat(provider.kept(link("https://news.example/a"), null, null, 41)).isEqualTo(linked);
    assertThat(
            provider.keptAmong(
                tag("소풍"), 7L, List.of("https://x.example/1", "https://x.example/2")))
        .containsExactly("https://x.example/1");
    assertThat(
            provider.keptAmong(
                link("https://news.example/a"), null, List.of("https://x.example/3")))
        .containsExactly("https://x.example/3");
  }

  @Test
  void aNameNoNoteCanCarryAnEmptyAddressOrNoAddressesToCheckReadNothing() {
    for (NoteTopicReader.Carrying nothing :
        List.of(tag(" "), tag("x".repeat(41)), link(" "), link(null))) {
      assertThat(provider.kept(nothing, 7L, null, 41)).isEmpty();
      assertThat(provider.keptAmong(nothing, 7L, List.of("https://x.example/1"))).isEmpty();
    }
    assertThat(provider.keptAmong(tag("소풍"), 7L, List.of())).isEmpty();
    assertThat(provider.keptAmong(link("https://news.example/a"), 7L, List.of())).isEmpty();

    verifyNoInteractions(feeds);
  }
}
