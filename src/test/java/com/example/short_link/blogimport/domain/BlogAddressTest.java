package com.example.short_link.blogimport.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class BlogAddressTest {

  @ParameterizedTest
  @CsvSource({
    "https://blog.naver.com/NaverOfficial, NAVER, naverofficial, https://blog.naver.com/naverofficial, https://rss.blog.naver.com/naverofficial.xml",
    "blog.naver.com/naverofficial/224429356463?fromRss=true, NAVER, naverofficial, https://blog.naver.com/naverofficial, https://rss.blog.naver.com/naverofficial.xml",
    "https://m.blog.naver.com/naverofficial, NAVER, naverofficial, https://blog.naver.com/naverofficial, https://rss.blog.naver.com/naverofficial.xml",
    "https://blog.naver.com/PostView.naver?blogId=naver_diary&logNo=1, NAVER, naver_diary, https://blog.naver.com/naver_diary, https://rss.blog.naver.com/naver_diary.xml",
    "https://rss.blog.naver.com/naver_diary.xml, NAVER, naver_diary, https://blog.naver.com/naver_diary, https://rss.blog.naver.com/naver_diary.xml",
    "http://blogpeople.blog.me, NAVER, blogpeople, https://blog.naver.com/blogpeople, https://rss.blog.naver.com/blogpeople.xml",
    "https://notice.tistory.com/2705, TISTORY, notice, https://notice.tistory.com, https://notice.tistory.com/rss",
    "notice.tistory.com, TISTORY, notice, https://notice.tistory.com, https://notice.tistory.com/rss",
    "https://velog.io/@Velopert/posts, VELOG, velopert, https://velog.io/@velopert, https://v2.velog.io/rss/@velopert",
    "https://velog.io/%40velopert, VELOG, velopert, https://velog.io/@velopert, https://v2.velog.io/rss/@velopert",
    "https://v2.velog.io/rss/@velopert, VELOG, velopert, https://velog.io/@velopert, https://v2.velog.io/rss/@velopert",
    "https://v2.velog.io/rss/velopert, VELOG, velopert, https://velog.io/@velopert, https://v2.velog.io/rss/@velopert",
    "https://brunch.co.kr/@@2xr/432, BRUNCH, @@2xr, https://brunch.co.kr/@@2xr, https://brunch.co.kr/rss/@@2xr",
    "https://brunch.co.kr/rss/@@2xr, BRUNCH, @@2xr, https://brunch.co.kr/@@2xr, https://brunch.co.kr/rss/@@2xr",
  })
  void supportedAddressesNameTheirBlogAndFeed(
      String raw, ImportPlatform platform, String id, String blogUrl, String feedUrl) {
    BlogAddress address = BlogAddress.parse(raw).orElseThrow();

    assertThat(address.platform()).isEqualTo(platform);
    assertThat(address.id()).isEqualTo(id);
    assertThat(address.blogUrl()).isEqualTo(blogUrl);
    assertThat(address.feedUrl()).isEqualTo(feedUrl);
    assertThat(address.feedKnown()).isTrue();
    assertThat(address.blogKey()).isEqualTo(platform.name().toLowerCase() + ":" + id);
  }

  @Test
  void aBrunchHandleNeedsItsProfilePageForTheFeed() {
    BlogAddress handle = BlogAddress.parse("https://brunch.co.kr/@brunch/12").orElseThrow();

    assertThat(handle.feedKnown()).isFalse();
    assertThat(handle.blogUrl()).isEqualTo("https://brunch.co.kr/@brunch");

    BlogAddress resolved = handle.withBrunchFeed("2xr");
    assertThat(resolved.feedUrl()).isEqualTo("https://brunch.co.kr/rss/@@2xr");
    assertThat(resolved.blogKey()).isEqualTo("brunch:@@2xr");
    assertThat(resolved.blogUrl()).isEqualTo("https://brunch.co.kr/@brunch");
    assertThatThrownBy(() -> handle.withBrunchFeed("../x"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> BlogAddress.parse("velog.io/@velopert").orElseThrow().withBrunchFeed("2xr"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(
      strings = {
        "https://medium.com/@medium",
        "https://example.com/blog",
        "javascript:alert(1)",
        "ftp://blog.naver.com/x",
        "https://tistory.com",
        "https://www.tistory.com/",
        "https://a.b.tistory.com",
        "https://blog.naver.com/",
        "https://blog.naver.com/PostView.naver?logNo=1",
        "https://blog.naver.com/bad.id",
        "https://velog.io/",
        "https://velog.io/@bad!id",
        "https://brunch.co.kr/",
        "https://brunch.co.kr/rss/@handle",
        "https://brunch.co.kr/@@bad-id",
        "https://brunch.co.kr/@bad handle",
        "http://[::1",
        "   ",
      })
  void everythingElseIsUnsupported(String raw) {
    assertThat(BlogAddress.parse(raw)).isEmpty();
  }

  @Test
  void overlongInputIsUnsupported() {
    assertThat(BlogAddress.parse("https://velog.io/@" + "a".repeat(600))).isEmpty();
  }
}
