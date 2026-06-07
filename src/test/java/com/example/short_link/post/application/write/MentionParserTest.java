package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class MentionParserTest {

  @Test
  void parsesDistinctHandlesInFirstSeenOrder() {
    assertThat(MentionParser.parse("hi @alice and @bob, also @alice again"))
        .containsExactly("alice", "bob");
  }

  @Test
  void doesNotMatchInsideAnEmail() {
    assertThat(MentionParser.parse("reach me at john@example.com please")).isEmpty();
  }

  @Test
  void ignoresInvalidHandles() {
    // too short (<3), uppercase, leading underscore — none match the username grammar.
    assertThat(MentionParser.parse("@ab @Alice @_nope")).isEmpty();
  }

  @Test
  void capsAtMaxMentions() {
    String body =
        IntStream.range(0, MentionParser.MAX_MENTIONS + 5)
            .mapToObj(i -> "@user" + i)
            .collect(Collectors.joining(" "));

    assertThat(MentionParser.parse(body)).hasSize(MentionParser.MAX_MENTIONS);
  }

  @Test
  void emptyForBlank() {
    assertThat(MentionParser.parse("  ")).isEmpty();
    assertThat(MentionParser.parse(null)).isEmpty();
  }
}
