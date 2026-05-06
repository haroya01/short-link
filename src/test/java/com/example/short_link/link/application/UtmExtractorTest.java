package com.example.short_link.link.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UtmExtractorTest {

  @Test
  void extractsAllUtmParams() {
    UtmParams utm =
        UtmExtractor.extract(
            "https://example.com/path?utm_source=insta&utm_medium=cpc&utm_campaign=spring&utm_term=shoe&utm_content=banner");

    assertThat(utm.source()).isEqualTo("insta");
    assertThat(utm.medium()).isEqualTo("cpc");
    assertThat(utm.campaign()).isEqualTo("spring");
    assertThat(utm.term()).isEqualTo("shoe");
    assertThat(utm.content()).isEqualTo("banner");
  }

  @Test
  void returnsEmptyForNullUrl() {
    assertThat(UtmExtractor.extract(null)).isEqualTo(UtmParams.empty());
  }

  @Test
  void returnsEmptyForUrlWithoutQuery() {
    assertThat(UtmExtractor.extract("https://example.com/path")).isEqualTo(UtmParams.empty());
  }

  @Test
  void returnsEmptyForUrlWithEmptyQuery() {
    assertThat(UtmExtractor.extract("https://example.com/path?")).isEqualTo(UtmParams.empty());
  }

  @Test
  void decodesPercentEncodedValues() {
    UtmParams utm = UtmExtractor.extract("https://example.com?utm_source=hello%20world");
    assertThat(utm.source()).isEqualTo("hello world");
  }

  @Test
  void ignoresFragment() {
    UtmParams utm = UtmExtractor.extract("https://example.com?utm_source=ig#section");
    assertThat(utm.source()).isEqualTo("ig");
  }

  @Test
  void ignoresNonUtmParams() {
    UtmParams utm = UtmExtractor.extract("https://example.com?id=42&utm_source=ig&page=1");
    assertThat(utm.source()).isEqualTo("ig");
  }

  @Test
  void leavesMissingParamsAsNull() {
    UtmParams utm = UtmExtractor.extract("https://example.com?utm_source=ig");
    assertThat(utm.source()).isEqualTo("ig");
    assertThat(utm.medium()).isNull();
    assertThat(utm.campaign()).isNull();
  }

  @Test
  void skipsEmptyPairs() {
    UtmParams utm = UtmExtractor.extract("https://example.com?&utm_source=ig&&utm_medium=cpc&");
    assertThat(utm.source()).isEqualTo("ig");
    assertThat(utm.medium()).isEqualTo("cpc");
  }

  @Test
  void skipsPairsWithoutEquals() {
    UtmParams utm = UtmExtractor.extract("https://example.com?flag&utm_source=ig");
    assertThat(utm.source()).isEqualTo("ig");
  }

  @Test
  void skipsInvalidPercentEncoding() {
    UtmParams utm = UtmExtractor.extract("https://example.com?utm_source=%ZZ&utm_medium=cpc");
    assertThat(utm.source()).isNull();
    assertThat(utm.medium()).isEqualTo("cpc");
  }
}
