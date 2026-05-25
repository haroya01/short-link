package com.example.short_link.profile.domain.contact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.profile.exception.ProfileException;
import org.junit.jupiter.api.Test;

class TextBlockBodyTest {

  @Test
  void legacyPlainMarkdownIsWrappedIntoInlineJson() {
    String out = TextBlockBody.normalize("## My Title\n\nHello world");
    assertThat(out).contains("\"body\":\"## My Title\\n\\nHello world\"");
    assertThat(out).contains("\"layout\":\"inline\"");
    assertThat(out).contains("\"accent\":null");
    assertThat(out).contains("\"icon\":null");
  }

  @Test
  void jsonPayloadRoundTripsWithAllFields() {
    String out =
        TextBlockBody.normalize(
            "{\"body\":\"Heads up!\",\"layout\":\"card\",\"accent\":\"amber\",\"icon\":\"💡\"}");
    assertThat(out).contains("\"body\":\"Heads up!\"");
    assertThat(out).contains("\"layout\":\"card\"");
    assertThat(out).contains("\"accent\":\"amber\"");
    assertThat(out).contains("\"icon\":\"💡\"");
  }

  @Test
  void unknownLayoutFallsBackToInline() {
    // Forward compat — a frontend rolled out ahead of a backend shouldn't 400 every write.
    String out = TextBlockBody.normalize("{\"body\":\"x\",\"layout\":\"futureLayout\"}");
    assertThat(out).contains("\"layout\":\"inline\"");
  }

  @Test
  void unknownAccentDropsToNull() {
    String out = TextBlockBody.normalize("{\"body\":\"x\",\"accent\":\"futureColor\"}");
    assertThat(out).contains("\"accent\":null");
  }

  @Test
  void acceptsAllFiveAccents() {
    for (String accent : new String[] {"blue", "amber", "green", "red", "violet"}) {
      String out = TextBlockBody.normalize("{\"body\":\"x\",\"accent\":\"" + accent + "\"}");
      assertThat(out).contains("\"accent\":\"" + accent + "\"");
    }
  }

  @Test
  void acceptsAllThreeLayouts() {
    for (String layout : new String[] {"inline", "card", "quote"}) {
      String out = TextBlockBody.normalize("{\"body\":\"x\",\"layout\":\"" + layout + "\"}");
      assertThat(out).contains("\"layout\":\"" + layout + "\"");
    }
  }

  @Test
  void emptyBodyRejected() {
    assertThatThrownBy(() -> TextBlockBody.normalize("")).isInstanceOf(ProfileException.class);
    assertThatThrownBy(() -> TextBlockBody.normalize("   ")).isInstanceOf(ProfileException.class);
    assertThatThrownBy(() -> TextBlockBody.normalize("{\"body\":\"  \"}"))
        .isInstanceOf(ProfileException.class);
  }

  @Test
  void bodyOver2000CharsRejected() {
    String huge = "a".repeat(2001);
    assertThatThrownBy(() -> TextBlockBody.normalize(huge)).isInstanceOf(ProfileException.class);
  }

  @Test
  void markdownStartingWithCurlyBraceIsTreatedAsMarkdownWhenJsonFails() {
    // Edge case — the prefix-check optimization shouldn't reject legitimate markdown that happens
    // to start with '{'. Json parse fails → fall back to legacy markdown path.
    String out = TextBlockBody.normalize("{not really json after all}");
    assertThat(out).contains("\"body\":\"{not really json after all}\"");
    assertThat(out).contains("\"layout\":\"inline\"");
  }

  @Test
  void iconTrimmedAndBlankBecomesNull() {
    String out = TextBlockBody.normalize("{\"body\":\"x\",\"icon\":\"   \"}");
    assertThat(out).contains("\"icon\":null");
  }
}
