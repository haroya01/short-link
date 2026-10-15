package com.example.short_link.blogimport.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.blogimport.application.feed.FeedEntry;
import com.example.short_link.blogimport.application.feed.HtmlBlockConverter;
import com.example.short_link.blogimport.domain.FeedValidators;
import com.example.short_link.blogimport.domain.ImportPlatform;
import com.example.short_link.blogimport.exception.BlogImportErrorCode;
import com.example.short_link.blogimport.exception.BlogImportException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class BlogFeedsTest {

  private static final Instant NOW = Instant.parse("2026-10-16T00:00:00Z");

  private final BlogFeedFetcher fetcher = mock(BlogFeedFetcher.class);
  private final RobotsGate robots = mock(RobotsGate.class);
  private final BlogFeeds feeds =
      new BlogFeeds(
          fetcher,
          new HtmlBlockConverter(JsonMapper.builder().build()),
          robots,
          Clock.fixed(NOW, ZoneOffset.UTC));

  private void serve(String url, String body) {
    when(fetcher.fetch(eq(url), any()))
        .thenReturn(new BlogFeedFetcher.Fetched(body, FeedValidators.NONE, false));
  }

  private static String fixture(String name) throws Exception {
    return Files.readString(Path.of("src/test/resources/blog-import/" + name));
  }

  @Test
  void naverIsASummaryFeedWithTrackingFreeAddresses() throws Exception {
    serve("https://rss.blog.naver.com/naverofficial.xml", fixture("naver.xml"));

    BlogFeed feed = feeds.read(feeds.parse("https://blog.naver.com/naverofficial"));

    assertThat(feed.fullContent()).isFalse();
    assertThat(feed.title()).isEqualTo("네이버 공식블로그");
    assertThat(feed.languageTag()).isEqualTo("ko");
    assertThat(feed.posts())
        .extracting(BlogFeed.Post::url)
        .containsExactly(
            "https://blog.naver.com/naverofficial/224429356463",
            "https://blog.naver.com/naverofficial/224428038966",
            "https://blog.naver.com/naverofficial/224427556966");
    assertThat(feed.describes("네이버 다이어리")).isTrue();
    assertThat(feed.describes("kurl-ABCDEF")).isFalse();
    assertThat(feed.describes(null)).isFalse();
  }

  @Test
  void tistoryWithMarkupIsFullAndVelogAlwaysIs() throws Exception {
    serve("https://notice.tistory.com/rss", fixture("tistory.xml"));
    serve("https://v2.velog.io/rss/@velopert", fixture("velog.xml"));

    assertThat(feeds.read(feeds.parse("notice.tistory.com")).fullContent()).isTrue();
    BlogFeed velog = feeds.read(feeds.parse("velog.io/@velopert"));
    assertThat(velog.fullContent()).isTrue();
    assertThat(velog.address().platform()).isEqualTo(ImportPlatform.VELOG);
  }

  @Test
  void aTistoryFeedOfPlainSummariesIsNotFull() {
    serve(
        "https://partial.tistory.com/rss",
        "<rss><channel><title>p</title><language>en-US</language><item><title>a</title>"
            + "<link>https://partial.tistory.com/1#comment</link>"
            + "<description>just the opening lines...</description></item></channel></rss>");

    BlogFeed feed = feeds.read(feeds.parse("partial.tistory.com"));

    assertThat(feed.fullContent()).isFalse();
    assertThat(feed.languageTag()).isEqualTo("en");
    assertThat(feed.posts().get(0).url()).isEqualTo("https://partial.tistory.com/1");
  }

  @Test
  void aBrunchHandleFindsItsFeedOnTheProfilePage() throws Exception {
    serve(
        "https://brunch.co.kr/@brunch",
        "<html><head><link rel=\"alternate\" type=\"application/rss+xml\""
            + " href=\"https://brunch.co.kr/rss/@@2xr\"></head></html>");
    serve("https://brunch.co.kr/rss/@@2xr", fixture("brunch.xml"));

    BlogFeed feed = feeds.read(feeds.parse("https://brunch.co.kr/@brunch"));

    assertThat(feed.address().blogKey()).isEqualTo("brunch:@@2xr");
    assertThat(feed.fullContent()).isFalse();
    assertThat(feed.posts()).hasSize(3);
  }

  @Test
  void aBrunchPageWithoutAFeedIsNotFound() {
    serve("https://brunch.co.kr/@nobody", "<html></html>");

    assertThatThrownBy(() -> feeds.read(feeds.parse("brunch.co.kr/@nobody")))
        .isInstanceOfSatisfying(
            BlogImportException.class,
            e -> assertThat(e.errorCode()).isEqualTo(BlogImportErrorCode.IMPORT_FEED_NOT_FOUND));
  }

  @Test
  void anEmptyChannelIsNotFoundAndAForeignDocumentIsInvalid() {
    serve(
        "https://rss.blog.naver.com/ghost.xml",
        "<rss><channel><title/><link/><description/></channel></rss>");
    serve("https://ghost.tistory.com/rss", "<html>moved</html>");

    assertThatThrownBy(() -> feeds.read(feeds.parse("blog.naver.com/ghost")))
        .isInstanceOfSatisfying(
            BlogImportException.class,
            e -> assertThat(e.errorCode()).isEqualTo(BlogImportErrorCode.IMPORT_FEED_NOT_FOUND));
    assertThatThrownBy(() -> feeds.read(feeds.parse("ghost.tistory.com")))
        .isInstanceOfSatisfying(
            BlogImportException.class,
            e -> assertThat(e.errorCode()).isEqualTo(BlogImportErrorCode.IMPORT_FEED_INVALID));
  }

  @Test
  void unsupportedAddressesAreRejectedUpFront() {
    assertThatThrownBy(() -> feeds.parse("https://medium.com/@medium"))
        .isInstanceOfSatisfying(
            BlogImportException.class,
            e ->
                assertThat(e.errorCode()).isEqualTo(BlogImportErrorCode.IMPORT_UNSUPPORTED_SOURCE));
  }

  @Test
  void entriesWithoutAnHttpAddressOrRepeatedAreSkipped() {
    serve(
        "https://dup.tistory.com/rss",
        "<rss><channel><title></title><language>ja</language>"
            + "<item><title>a</title><link>https://dup.tistory.com/1?x=1</link></item>"
            + "<item><title>b</title><link>https://dup.tistory.com/1</link></item>"
            + "<item><title>c</title><link>javascript:alert(1)</link></item>"
            + "<item><title>d</title><guid>https://dup.tistory.com/2</guid></item>"
            + "</channel></rss>");

    BlogFeed feed = feeds.read(feeds.parse("dup.tistory.com"));

    assertThat(feed.title()).isEqualTo("https://dup.tistory.com");
    assertThat(feed.languageTag()).isEqualTo("ja");
    assertThat(feed.posts())
        .extracting(BlogFeed.Post::url)
        .containsExactly("https://dup.tistory.com/1", "https://dup.tistory.com/2");
  }

  @Test
  void canonicalAddressesDropQueriesAndFragments() {
    FeedEntry naver =
        new FeedEntry(
            "t",
            "https://blog.naver.com/a/1?fromRss=true",
            "https://blog.naver.com/a/1",
            null,
            "",
            List.of());
    FeedEntry broken = new FeedEntry("t", "https://exa mple.com/x", null, null, "", List.of());
    FeedEntry noHost = new FeedEntry("t", "https:///x", null, null, "", List.of());

    assertThat(BlogFeeds.canonical(ImportPlatform.NAVER, naver))
        .isEqualTo("https://blog.naver.com/a/1");
    assertThat(BlogFeeds.canonical(ImportPlatform.TISTORY, broken)).isNull();
    assertThat(BlogFeeds.canonical(ImportPlatform.TISTORY, noHost)).isNull();
    assertThat(
            BlogFeeds.canonical(
                ImportPlatform.VELOG,
                new FeedEntry(
                    "t", "https://velog.io/@a/" + "x".repeat(2100), null, null, "", List.of())))
        .isNull();
  }

  @Test
  void aFeedWithATitleButNoPostsIsEmptyNotMissing() {
    serve(
        "https://rss.blog.naver.com/quiet.xml",
        "<rss><channel><title>조용한 블로그</title><link>https://blog.naver.com/quiet</link>"
            + "<description>소개</description></channel></rss>");

    BlogFeed feed = feeds.read(feeds.parse("blog.naver.com/quiet"));

    assertThat(feed.posts()).isEmpty();
    assertThat(feed.title()).isEqualTo("조용한 블로그");
  }

  @Test
  void robotsAreAskedBeforeEveryFeedAndProfilePageRequest() {
    doThrow(new BlogImportException(BlogImportErrorCode.IMPORT_ROBOTS_DISALLOWED, "x"))
        .when(robots)
        .requireAllowed("https://rss.blog.naver.com/blocked.xml");

    assertThatThrownBy(() -> feeds.read(feeds.parse("blog.naver.com/blocked")))
        .isInstanceOfSatisfying(
            BlogImportException.class,
            e -> assertThat(e.errorCode()).isEqualTo(BlogImportErrorCode.IMPORT_ROBOTS_DISALLOWED));
    verify(fetcher, never()).fetch(any(), any());

    serve("https://brunch.co.kr/@writer", "<a href=\"https://brunch.co.kr/rss/@@9z\">rss</a>");
    serve("https://brunch.co.kr/rss/@@9z", "<rss><channel><title>t</title></channel></rss>");
    feeds.read(feeds.parse("brunch.co.kr/@writer"));
    verify(robots).requireAllowed("https://brunch.co.kr/@writer");
    verify(robots).requireAllowed("https://brunch.co.kr/rss/@@9z");
  }

  @Test
  void aConditionalReadSkipsAnUnchangedFeedAndCarriesNewValidators() {
    FeedValidators stored = new FeedValidators("\"v1\"", "Wed, 14 Oct 2026 00:00:00 GMT");
    when(fetcher.fetch("https://v2.velog.io/rss/@w", stored))
        .thenReturn(BlogFeedFetcher.Fetched.notModified(stored));
    assertThat(feeds.readIfChanged(feeds.parse("velog.io/@w"), stored)).isEmpty();

    FeedValidators fresh = new FeedValidators("\"v2\"", null);
    when(fetcher.fetch("https://v2.velog.io/rss/@w", FeedValidators.NONE))
        .thenReturn(
            new BlogFeedFetcher.Fetched(
                "<rss><channel><title>t</title></channel></rss>", fresh, false));
    assertThat(feeds.read(feeds.parse("velog.io/@w")).validators()).isEqualTo(fresh);
  }

  @Test
  void aRecentReadIsReusedForAMinuteOnly() throws Exception {
    serve("https://v2.velog.io/rss/@velopert", fixture("velog.xml"));
    BlogFeeds later =
        new BlogFeeds(
            fetcher,
            new HtmlBlockConverter(JsonMapper.builder().build()),
            robots,
            Clock.fixed(NOW.plus(BlogFeeds.RECENT), ZoneOffset.UTC));

    feeds.read(feeds.parse("velog.io/@velopert"));
    feeds.readRecent(feeds.parse("velog.io/@velopert"));
    verify(fetcher, times(1)).fetch(eq("https://v2.velog.io/rss/@velopert"), any());

    later.readRecent(feeds.parse("velog.io/@velopert"));
    verify(fetcher, times(2)).fetch(eq("https://v2.velog.io/rss/@velopert"), any());
  }

  @Test
  void theRecentCacheStaysSmall() {
    for (int i = 0; i < BlogFeeds.RECENT_ENTRIES + 5; i++) {
      serve(
          "https://b" + i + ".tistory.com/rss",
          "<rss><channel><title>t</title><item><title>a</title>"
              + "<link>https://b"
              + i
              + ".tistory.com/1</link></item></channel></rss>");
      feeds.readRecent(feeds.parse("b" + i + ".tistory.com"));
    }
    feeds.readRecent(feeds.parse("b0.tistory.com"));
    verify(fetcher, times(2)).fetch(eq("https://b0.tistory.com/rss"), any());
  }
}
