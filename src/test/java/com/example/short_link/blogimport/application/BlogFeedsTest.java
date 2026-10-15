package com.example.short_link.blogimport.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.short_link.blogimport.application.feed.FeedEntry;
import com.example.short_link.blogimport.application.feed.HtmlBlockConverter;
import com.example.short_link.blogimport.domain.ImportPlatform;
import com.example.short_link.blogimport.exception.BlogImportErrorCode;
import com.example.short_link.blogimport.exception.BlogImportException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class BlogFeedsTest {

  private final BlogFeedFetcher fetcher = mock(BlogFeedFetcher.class);
  private final BlogFeeds feeds =
      new BlogFeeds(fetcher, new HtmlBlockConverter(JsonMapper.builder().build()));

  private static String fixture(String name) throws Exception {
    return Files.readString(Path.of("src/test/resources/blog-import/" + name));
  }

  @Test
  void naverIsASummaryFeedWithTrackingFreeAddresses() throws Exception {
    when(fetcher.fetch("https://rss.blog.naver.com/naverofficial.xml"))
        .thenReturn(fixture("naver.xml"));

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
    when(fetcher.fetch("https://notice.tistory.com/rss")).thenReturn(fixture("tistory.xml"));
    when(fetcher.fetch("https://v2.velog.io/rss/@velopert")).thenReturn(fixture("velog.xml"));

    assertThat(feeds.read(feeds.parse("notice.tistory.com")).fullContent()).isTrue();
    BlogFeed velog = feeds.read(feeds.parse("velog.io/@velopert"));
    assertThat(velog.fullContent()).isTrue();
    assertThat(velog.address().platform()).isEqualTo(ImportPlatform.VELOG);
  }

  @Test
  void aTistoryFeedOfPlainSummariesIsNotFull() {
    when(fetcher.fetch("https://partial.tistory.com/rss"))
        .thenReturn(
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
    when(fetcher.fetch("https://brunch.co.kr/@brunch"))
        .thenReturn(
            "<html><head><link rel=\"alternate\" type=\"application/rss+xml\""
                + " href=\"https://brunch.co.kr/rss/@@2xr\"></head></html>");
    when(fetcher.fetch("https://brunch.co.kr/rss/@@2xr")).thenReturn(fixture("brunch.xml"));

    BlogFeed feed = feeds.read(feeds.parse("https://brunch.co.kr/@brunch"));

    assertThat(feed.address().blogKey()).isEqualTo("brunch:@@2xr");
    assertThat(feed.fullContent()).isFalse();
    assertThat(feed.posts()).hasSize(3);
  }

  @Test
  void aBrunchPageWithoutAFeedIsNotFound() {
    when(fetcher.fetch("https://brunch.co.kr/@nobody")).thenReturn("<html></html>");

    assertThatThrownBy(() -> feeds.read(feeds.parse("brunch.co.kr/@nobody")))
        .isInstanceOfSatisfying(
            BlogImportException.class,
            e -> assertThat(e.errorCode()).isEqualTo(BlogImportErrorCode.IMPORT_FEED_NOT_FOUND));
  }

  @Test
  void anEmptyChannelIsNotFoundAndAForeignDocumentIsInvalid() {
    when(fetcher.fetch("https://rss.blog.naver.com/ghost.xml"))
        .thenReturn("<rss><channel><title/><link/><description/></channel></rss>");
    when(fetcher.fetch("https://ghost.tistory.com/rss")).thenReturn("<html>moved</html>");

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
    when(fetcher.fetch("https://dup.tistory.com/rss"))
        .thenReturn(
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
}
