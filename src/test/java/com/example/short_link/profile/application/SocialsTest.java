package com.example.short_link.profile.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SocialsTest {

  @Test
  void normalizesValidPair() {
    String out = Socials.normalize("[{\"channel\":\"x\",\"url\":\"https://x.com/foo\"}]");
    assertThat(out).contains("\"channel\":\"x\"");
    assertThat(out).contains("\"url\":\"https://x.com/foo\"");
  }

  @Test
  void nullOrBlankYieldsNull() {
    assertThat(Socials.normalize(null)).isNull();
    assertThat(Socials.normalize("")).isNull();
    assertThat(Socials.normalize("   ")).isNull();
  }

  @Test
  void emptyArrayYieldsNull() {
    // [] is valid JSON but means "no socials" — same as null on the wire so the user can clear
    // their list. Persist as null to keep DB consistent (vs. literal "[]" which would be a
    // legitimate value for "explicitly set to empty").
    assertThat(Socials.normalize("[]")).isNull();
  }

  @Test
  void acceptsAllWhitelistedChannels() {
    for (String channel : Socials.ALLOWED) {
      String out =
          Socials.normalize(
              "[{\"channel\":\"" + channel + "\",\"url\":\"https://example.com/p\"}]");
      assertThat(out).contains("\"channel\":\"" + channel + "\"");
    }
  }

  @Test
  void rejectsUnknownChannel() {
    assertThatThrownBy(
            () -> Socials.normalize("[{\"channel\":\"myspace\",\"url\":\"https://myspace.com\"}]"))
        .isInstanceOf(InvalidUsernameException.class)
        .hasMessageContaining("unknown channel");
  }

  @Test
  void rejectsTooManyEntries() {
    // MAX=2 — try 3 and verify rejection.
    String json =
        "["
            + "{\"channel\":\"x\",\"url\":\"https://x.com/a\"},"
            + "{\"channel\":\"line\",\"url\":\"https://line.me/b\"},"
            + "{\"channel\":\"threads\",\"url\":\"https://threads.net/c\"}"
            + "]";
    assertThatThrownBy(() -> Socials.normalize(json))
        .isInstanceOf(InvalidUsernameException.class)
        .hasMessageContaining("up to " + Socials.MAX);
  }

  @Test
  void rejectsNonHttpScheme() {
    assertThatThrownBy(
            () -> Socials.normalize("[{\"channel\":\"x\",\"url\":\"javascript:alert(1)\"}]"))
        .isInstanceOf(InvalidUsernameException.class);
    assertThatThrownBy(() -> Socials.normalize("[{\"channel\":\"x\",\"url\":\"ftp://x.com\"}]"))
        .isInstanceOf(InvalidUsernameException.class);
  }

  @Test
  void rejectsUrlMissingHost() {
    assertThatThrownBy(() -> Socials.normalize("[{\"channel\":\"x\",\"url\":\"https://\"}]"))
        .isInstanceOf(InvalidUsernameException.class);
  }

  @Test
  void rejectsMalformedJson() {
    assertThatThrownBy(() -> Socials.normalize("not json"))
        .isInstanceOf(InvalidUsernameException.class)
        .hasMessageContaining("malformed");
  }

  @Test
  void normalizesChannelToLowercaseAndTrims() {
    String out = Socials.normalize("[{\"channel\":\"  X  \",\"url\":\"  https://x.com/foo  \"}]");
    assertThat(out).contains("\"channel\":\"x\"");
    assertThat(out).contains("\"url\":\"https://x.com/foo\"");
  }

  @Test
  void duplicateChannelKeepsFirstOnly() {
    // Dedup-by-channel keeps the first entry — a user can't double-stack the same icon. Two X
    // entries collapse into one and the second is silently dropped so a copy-paste mistake doesn't
    // 400 the entire save.
    String out =
        Socials.normalize(
            "["
                + "{\"channel\":\"x\",\"url\":\"https://x.com/first\"},"
                + "{\"channel\":\"x\",\"url\":\"https://x.com/second\"}"
                + "]");
    assertThat(out).contains("https://x.com/first");
    assertThat(out).doesNotContain("https://x.com/second");
  }

  @Test
  void blankChannelOrUrlEntriesAreSilentlyDropped() {
    // Editor sometimes sends placeholder rows with only one field filled in — should be ignored,
    // not error the whole save. Two entries (within MAX) where one is a placeholder collapse to
    // just the filled row.
    String out =
        Socials.normalize(
            "["
                + "{\"channel\":\"x\",\"url\":\"https://x.com/me\"},"
                + "{\"channel\":\"line\",\"url\":\"\"}"
                + "]");
    assertThat(out).contains("\"channel\":\"x\"");
    assertThat(out).doesNotContain("\"channel\":\"line\"");
  }

  @Test
  void allBlankEntriesYieldNull() {
    // After filtering blanks, if nothing remains the whole list collapses to null (= "clear").
    String out =
        Socials.normalize("[{\"channel\":\"\",\"url\":\"\"},{\"channel\":\"  \",\"url\":\"  \"}]");
    assertThat(out).isNull();
  }

  @Test
  void rejectsOverlongUrl() {
    String longUrl = "https://x.com/" + "a".repeat(300);
    assertThatThrownBy(() -> Socials.normalize("[{\"channel\":\"x\",\"url\":\"" + longUrl + "\"}]"))
        .isInstanceOf(InvalidUsernameException.class)
        .hasMessageContaining("too long");
  }

  @Test
  void toListReturnsEmptyForNullOrBlank() {
    assertThat(Socials.toList(null)).isEmpty();
    assertThat(Socials.toList("")).isEmpty();
    assertThat(Socials.toList("   ")).isEmpty();
  }

  @Test
  void toListReturnsEmptyForCorruptStoredJson() {
    // Defensive: a stored row that somehow contains malformed JSON shouldn't blow up the public
    // profile render. Return an empty list and let the rest of the page load.
    assertThat(Socials.toList("not json")).isEmpty();
  }

  @Test
  void roundTripsThroughToList() {
    String json = Socials.normalize("[{\"channel\":\"x\",\"url\":\"https://x.com/me\"}]");
    var list = Socials.toList(json);
    assertThat(list).hasSize(1);
    assertThat(list.get(0).channel()).isEqualTo("x");
    assertThat(list.get(0).url()).isEqualTo("https://x.com/me");
  }
}
