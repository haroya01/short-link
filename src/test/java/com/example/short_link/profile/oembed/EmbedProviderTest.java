package com.example.short_link.profile.oembed;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmbedProviderTest {

  @Test
  void resolvesYouTubeHosts() {
    assertThat(EmbedProvider.resolve("https://www.youtube.com/watch?v=abc"))
        .contains(EmbedProvider.YOUTUBE);
    assertThat(EmbedProvider.resolve("https://youtu.be/abc")).contains(EmbedProvider.YOUTUBE);
    assertThat(EmbedProvider.resolve("https://m.youtube.com/watch?v=abc"))
        .contains(EmbedProvider.YOUTUBE);
  }

  @Test
  void resolvesVimeo() {
    assertThat(EmbedProvider.resolve("https://vimeo.com/12345")).contains(EmbedProvider.VIMEO);
  }

  @Test
  void resolvesSpotify() {
    assertThat(EmbedProvider.resolve("https://open.spotify.com/track/abc"))
        .contains(EmbedProvider.SPOTIFY);
  }

  @Test
  void rejectsUnknownHost() {
    assertThat(EmbedProvider.resolve("https://example.com/x")).isEmpty();
    assertThat(EmbedProvider.resolve("https://evil.com/x")).isEmpty();
  }

  @Test
  void rejectsHostMasqueradingAsYouTubeSubdomain() {
    // exact-host match (Set.contains), not endsWith — prevents "youtube.com.evil.com" smuggling.
    assertThat(EmbedProvider.resolve("https://youtube.com.evil.com/")).isEmpty();
    assertThat(EmbedProvider.resolve("https://evil-youtube.com/")).isEmpty();
  }

  @Test
  void rejectsNonHttpSchemes() {
    assertThat(EmbedProvider.resolve("file:///etc/passwd")).isEmpty();
    assertThat(EmbedProvider.resolve("javascript:alert(1)")).isEmpty();
    assertThat(EmbedProvider.resolve("ftp://youtube.com/")).isEmpty();
  }

  @Test
  void rejectsMalformed() {
    assertThat(EmbedProvider.resolve(null)).isEmpty();
    assertThat(EmbedProvider.resolve("")).isEmpty();
    assertThat(EmbedProvider.resolve("not a url")).isEmpty();
    assertThat(EmbedProvider.resolve("https://")).isEmpty();
  }

  @Test
  void caseInsensitiveHost() {
    assertThat(EmbedProvider.resolve("https://WWW.YouTube.COM/watch?v=abc"))
        .contains(EmbedProvider.YOUTUBE);
  }
}
