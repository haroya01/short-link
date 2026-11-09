package com.example.short_link.link.classifier.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ReferrerChannelClassifierTest {

  private final ReferrerChannelClassifier classifier = new ReferrerChannelClassifier();

  @Test
  void nullReferrerIsDirect() {
    assertThat(classifier.classify(null)).isEqualTo("direct");
  }

  @Test
  void blankReferrerIsDirect() {
    assertThat(classifier.classify("   ")).isEqualTo("direct");
  }

  @Test
  void instagramIsSocial() {
    assertThat(classifier.classify("https://www.instagram.com/p/abc")).isEqualTo("social");
  }

  @Test
  void youtubeIsSocial() {
    assertThat(classifier.classify("https://www.youtube.com/watch?v=xyz")).isEqualTo("social");
  }

  @Test
  void googleIsSearch() {
    assertThat(classifier.classify("https://www.google.com/search?q=foo")).isEqualTo("search");
  }

  @Test
  void naverIsSearch() {
    assertThat(classifier.classify("https://search.naver.com/path")).isEqualTo("search");
  }

  @Test
  void gmailIsEmail() {
    assertThat(classifier.classify("https://mail.google.com/u/0")).isEqualTo("email");
  }

  @Test
  void unknownDomainIsOther() {
    assertThat(classifier.classify("https://random-blog.example/post")).isEqualTo("other");
  }

  @Test
  void invalidUrlIsOther() {
    assertThat(classifier.classify("not a url at all")).isEqualTo("other");
  }

  @ParameterizedTest
  @CsvSource({
    "m.mail.naver.com, email",
    "m.mail.daum.net, email",
    "u.mail.google.com, email",
    "m.search.naver.com, search",
    "m.facebook.com, social"
  })
  void aSubdomainTakesTheChannelOfItsClosestListedDomain(String host, String channel) {
    assertThat(classifier.classifyHost(host)).isEqualTo(channel);
  }
}
