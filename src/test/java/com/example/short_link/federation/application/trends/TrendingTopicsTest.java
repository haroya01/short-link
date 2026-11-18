package com.example.short_link.federation.application.trends;

import static com.example.short_link.federation.application.trends.TopicFixtures.JSON;
import static com.example.short_link.federation.application.trends.TopicFixtures.linkRow;
import static com.example.short_link.federation.application.trends.TopicFixtures.linksOf;
import static com.example.short_link.federation.application.trends.TopicFixtures.ok;
import static com.example.short_link.federation.application.trends.TopicFixtures.rows;
import static com.example.short_link.federation.application.trends.TopicFixtures.status;
import static com.example.short_link.federation.application.trends.TopicFixtures.tagRow;
import static com.example.short_link.federation.application.trends.TopicFixtures.tagsOf;
import static com.example.short_link.federation.application.trends.TopicFixtures.uri;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.note.NoteTopicReader;
import com.example.short_link.federation.application.FederationHttp;
import com.example.short_link.federation.application.signature.Signer;
import com.example.short_link.federation.application.signature.SigningKeys;
import com.example.short_link.federation.domain.ServerBlockSeverity;
import com.example.short_link.federation.domain.repository.FederationDomainBlockRepository;
import com.example.short_link.federation.domain.repository.RemoteAccountStandingReader;
import com.example.short_link.federation.domain.topics.Topic;
import com.example.short_link.federation.domain.topics.TopicArticle;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.node.ObjectNode;

@ExtendWith(MockitoExtension.class)
class TrendingTopicsTest {

  private static final Instant NOW = Instant.parse("2026-11-18T09:00:00Z");
  private static final Duration PATIENCE = Duration.ofSeconds(4);
  private static final TrendsProperties PROPS =
      new TrendsProperties(
          true, List.of("a.example", "b.example", "c.example"), 0, 0, null, PATIENCE);

  @Mock private FederationHttp http;
  @Mock private SigningKeys signingKeys;
  @Mock private FederationDomainBlockRepository serverBlocks;
  @Mock private RemoteAccountStandingReader standings;
  @Mock private NoteTopicReader notes;

  private final Signer instance = new Signer("https://kurl.me/ap/instance#main-key", null);
  private TrendingTopics topics;

  @BeforeEach
  void topics() {
    lenient().when(signingKeys.forInstance()).thenReturn(instance);
    lenient().when(serverBlocks.severityFor(anyString())).thenReturn(Optional.empty());
    lenient().when(standings.idsOf(any())).thenReturn(Map.of());
    lenient()
        .when(http.getJson(any(), any(), any()))
        .thenReturn(new FederationHttp.Result.Failed(404, "Not Found"));
    topics =
        new TrendingTopics(
            PROPS,
            signingKeys,
            serverBlocks,
            notes,
            new TopicTimelines(
                PROPS, http, signingKeys, serverBlocks, standings, TopicFixtures.READER, JSON),
            TopicFixtures.URLS,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private void tags(String host, String... rows) {
    lenient().when(http.getJson(tagsOf(host), instance, PATIENCE)).thenReturn(ok(rows(rows)));
  }

  private void links(String host, String... rows) {
    lenient().when(http.getJson(linksOf(host), instance, PATIENCE)).thenReturn(ok(rows(rows)));
  }

  private static URI sampleOf(String host, String tag) {
    return URI.create("https://" + host + "/api/v1/timelines/tag/" + tag + "?limit=20");
  }

  private void sample(String host, String tag, String language, int posts) {
    List<Object> statuses = new ArrayList<>();
    for (int n = 1; n <= posts; n++) {
      statuses.add(status(uri(host, tag + n, n), NOW.minusSeconds(n), language));
    }
    lenient()
        .when(http.getJson(sampleOf(host, tag), instance, PATIENCE))
        .thenReturn(ok(JSON.writeValueAsString(statuses)));
  }

  private List<String> ids() {
    return topics.held().topics().stream().map(Topic::id).toList();
  }

  private Topic topic(String id) {
    return topics.held().find(id).orElseThrow();
  }

  @Test
  void everyServersTagsAndLinksBecomeOnePoolWithThisServersOwn() {
    tags("a.example", tagRow("소풍", 2, 2), tagRow("기차역", 1, 1));
    links("a.example", linkRow("https://news.example/a", "다리가 다시 열린다", "ko", 6, 2));
    tags("b.example", tagRow("소풍", 5, 4));
    links("b.example");
    when(notes.trendingTags(NOW, 2, 2, 20))
        .thenReturn(List.of(new NoteTopicReader.Tag("소풍", 2), new NoteTopicReader.Tag("우리동네", 3)));
    when(notes.trendingLinks(NOW, 2, 2, 20))
        .thenReturn(
            List.of(new NoteTopicReader.Link("https://news.example/a", "다리가 다시 열린다", null, 2)));

    topics.refresh();

    assertThat(ids()).containsExactly("tag:소풍", "link:https://news.example/a", "tag:우리동네");
    Topic picnic = topic("tag:소풍");
    assertThat(picnic.people()).isEqualTo(9);
    assertThat(picnic.language()).isEqualTo("ko");
    assertThat(picnic.servers()).containsExactly("a.example", "b.example", "kurl.me");
    assertThat(topics.elsewhere(picnic)).containsExactly("a.example", "b.example");
    assertThat(topic("link:https://news.example/a").servers())
        .containsExactly("a.example", "kurl.me");
    assertThat(topic("tag:우리동네").servers()).containsExactly("kurl.me");
    assertThat(topics.elsewhere(topic("tag:우리동네"))).isEmpty();
    verify(signingKeys, times(1)).forInstance();
  }

  @Test
  void aBlockedServerIsNotAskedSoATopicTrendingOnlyThereIsGone() {
    tags("a.example", tagRow("소풍", 5, 4));
    tags("b.example", tagRow("소풍", 9, 9), tagRow("기차역", 9, 9));
    links("b.example", linkRow("https://news.example/b", "새 여객선", "ko", 9, 9));
    when(serverBlocks.severityFor("b.example")).thenReturn(Optional.of(ServerBlockSeverity.LIMIT));

    topics.refresh();

    assertThat(ids()).containsExactly("tag:소풍");
    assertThat(topic("tag:소풍").people()).isEqualTo(9);
    assertThat(topic("tag:소풍").servers()).containsExactly("a.example");
    verify(http, never()).getJson(eq(tagsOf("b.example")), any(), any());
    verify(http, never()).getJson(eq(linksOf("b.example")), any(), any());
  }

  @Test
  void whenEveryServerIsBlockedNothingIsAskedAndOnlyThisServersOwnRemain() {
    when(serverBlocks.severityFor(anyString()))
        .thenReturn(Optional.of(ServerBlockSeverity.SUSPEND));
    when(notes.trendingTags(NOW, 2, 2, 20))
        .thenReturn(List.of(new NoteTopicReader.Tag("harbor", 3)));

    topics.refresh();

    assertThat(ids()).containsExactly("tag:harbor");
    assertThat(topic("tag:harbor").language()).isNull();
    verify(http, never()).getJson(any(), any(), any());
    verify(signingKeys, never()).forInstance();
  }

  @Test
  void aServerAnsweringNeitherListWaitsLongerEachTimeAndOneListIsEnoughToCount() {
    tags("a.example", tagRow("소풍", 5, 4));
    tags("c.example", tagRow("기차역", 5, 4));

    topics.refresh();
    topics.refresh();
    verify(http, times(1)).getJson(eq(tagsOf("b.example")), any(), any());
    verify(http, times(2)).getJson(eq(tagsOf("a.example")), any(), any());
    verify(http, times(2)).getJson(eq(linksOf("a.example")), any(), any());

    topics.refresh();
    verify(http, times(2)).getJson(eq(tagsOf("b.example")), any(), any());
    topics.refresh();
    topics.refresh();
    verify(http, times(2)).getJson(eq(tagsOf("b.example")), any(), any());

    tags("b.example", tagRow("우리동네", 5, 4));
    topics.refresh();
    topics.refresh();
    verify(http, times(4)).getJson(eq(tagsOf("b.example")), any(), any());
    assertThat(ids()).containsExactlyInAnyOrder("tag:소풍", "tag:기차역", "tag:우리동네");
  }

  @Test
  void aRefreshReplacesThePoolSoATopicNoServerNamesAnyMoreIsGone() {
    tags("a.example", tagRow("소풍", 5, 4));
    topics.refresh();
    assertThat(ids()).containsExactly("tag:소풍");

    tags("a.example", tagRow("기차역", 5, 4));
    topics.refresh();

    assertThat(ids()).containsExactly("tag:기차역");
  }

  @Test
  void aNameInHangulOrKanaNeedsNoSampleAndOthersAreSampledWhereTheyPlaceBest() {
    tags("a.example", tagRow("소풍", 9, 9), tagRow("やまびこ", 8, 8), tagRow("harbor", 4, 4));
    tags("b.example", tagRow("harbor", 3, 3), tagRow("山彦", 5, 5));
    sample("b.example", "harbor", "en", 3);
    sample("b.example", "%E5%B1%B1%E5%BD%A6", "ja", 2);

    topics.refresh();

    assertThat(topic("tag:소풍").language()).isEqualTo("ko");
    assertThat(topic("tag:やまびこ").language()).isEqualTo("ja");
    assertThat(topic("tag:harbor").language()).isEqualTo("en");
    assertThat(topic("tag:山彦").language()).isEqualTo("ja");
    verify(http, times(1)).getJson(eq(sampleOf("b.example", "harbor")), any(), any());
    verify(http, never()).getJson(eq(sampleOf("a.example", "harbor")), any(), any());
    verify(http, times(2))
        .getJson(argThat(asked -> asked.getPath().contains("/timelines/tag/")), any(), any());
  }

  @Test
  void atMostTwentyTagsAreSampledARefreshMostPeopleFirst() {
    List<String> a = new ArrayList<>();
    List<String> b = new ArrayList<>();
    for (int n = 0; n < 26; n++) {
      (n % 2 == 0 ? a : b).add(tagRow("word%02d".formatted(n), 60 - n, 0));
      sample(n % 2 == 0 ? "a.example" : "b.example", "word%02d".formatted(n), "en", 1);
    }
    a.add(tagRow("few", 1, 1));
    tags("a.example", a.toArray(String[]::new));
    tags("b.example", b.toArray(String[]::new));

    topics.refresh();

    verify(http, times(20))
        .getJson(argThat(asked -> asked.getPath().contains("/timelines/tag/")), any(), any());
    assertThat(topic("tag:word19").language()).isEqualTo("en");
    assertThat(topic("tag:word20").language()).isNull();
    verify(http, never()).getJson(eq(sampleOf("a.example", "few")), any(), any());
  }

  @Test
  void aSampleSeedsTheTagsPostsSoTheFirstReaderWaitsForNobody() {
    tags("a.example", tagRow("harbor", 4, 4));
    sample("a.example", "harbor", "en", 3);

    topics.refresh();
    List<TrendingStatusReader.Read> seeded =
        topics
            .held()
            .posts(
                topic("tag:harbor"),
                NOW,
                () -> {
                  throw new AssertionError("a seeded topic was read again");
                });

    assertThat(seeded).hasSize(3);
    assertThat(topics.held().postsHeld()).isEqualTo(1);
  }

  @Test
  void aSampleWithNoReadablePostNamesTheLanguageAndLeavesThePostsToTheFirstReader() {
    tags("a.example", tagRow("harbor", 4, 4));
    ObjectNode reply = status(uri("a.example", "re", 1), NOW, "ja");
    reply.put("in_reply_to_id", "9");
    lenient()
        .when(http.getJson(sampleOf("a.example", "harbor"), instance, PATIENCE))
        .thenReturn(ok(JSON.writeValueAsString(List.of(reply))));

    topics.refresh();

    assertThat(topic("tag:harbor").language()).isEqualTo("ja");
    assertThat(topics.held().postsHeld()).isZero();
  }

  @Test
  void aTagWhoseSampleFailsOrThatTrendsOnlyHereStaysWithoutALanguage() {
    tags("a.example", tagRow("harbor", 4, 4));
    when(notes.trendingTags(NOW, 2, 2, 20))
        .thenReturn(List.of(new NoteTopicReader.Tag("lantern", 5)));

    topics.refresh();

    assertThat(topic("tag:harbor").language()).isNull();
    assertThat(topic("tag:lantern").language()).isNull();
    verify(http, times(1))
        .getJson(argThat(asked -> asked.getPath().contains("/timelines/tag/")), any(), any());
    assertThat(topics.held().postsHeld()).isZero();
  }

  @Test
  void articlesFoldUnderATagOnceItsLanguageIsKnown() {
    tags("a.example", tagRow("Yamabiko", 4, 4));
    links(
        "a.example",
        linkRow("https://news.example/ja", "新しいYamabikoの切符", "ja", 20, 10),
        linkRow("https://news.example/en", "Yamabiko tickets on sale", "en", 6, 2));
    sample("a.example", "Yamabiko", "ja", 2);

    topics.refresh();

    assertThat(ids()).containsExactly("tag:yamabiko", "link:https://news.example/en");
    Topic tag = topic("tag:yamabiko");
    assertThat(tag.people()).isEqualTo(30);
    assertThat(tag.related())
        .extracting(TopicArticle::url)
        .containsExactly("https://news.example/ja");
    assertThat(topics.held().find("link:https://news.example/ja")).isEmpty();
  }

  @Test
  void blockingAServerTakesItOutOfThePoolAtOnce() {
    tags("a.example", tagRow("소풍", 5, 4));
    tags("b.example", tagRow("소풍", 9, 9), tagRow("기차역", 9, 9));
    topics.refresh();

    topics.withdraw("b.example");

    assertThat(ids()).containsExactly("tag:소풍");
    assertThat(topic("tag:소풍").servers()).containsExactly("a.example");
    verify(notes, times(1)).trendingTags(any(), anyInt(), anyInt(), anyInt());
  }
}
