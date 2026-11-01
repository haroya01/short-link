package com.example.short_link.note.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.common.federation.CustomEmoji;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class NoteEntityTest {

  @Test
  void anExcerptIsOneLineCutAtEightyCodePoints() {
    assertThat(new NoteEntity(1L, "  first\nsecond\t third  ", null, null).excerpt())
        .isEqualTo("first second third");
    String emoji = "😀".repeat(81);
    assertThat(new NoteEntity(1L, emoji, null, null).excerpt()).isEqualTo("😀".repeat(80) + "…");
    assertThat(new NoteEntity(1L, "😀".repeat(80), null, null).excerpt())
        .isEqualTo("😀".repeat(80));
  }

  @Test
  void theEmojisColumnIsRewrittenOnlyWhenTheListChanges() {
    NoteEntity note = new NoteEntity(null, "boo :neoghost:", null, null);
    String asMysqlReturnsIt =
        "[{\"url\": \"https://m.example/e.png\", \"shortcode\": \"neoghost\"}]";
    ReflectionTestUtils.setField(note, "remoteEmojis", asMysqlReturnsIt);

    note.useRemoteEmojis(List.of(new CustomEmoji("neoghost", "https://m.example/e.png")));
    assertThat(note.getRemoteEmojis()).isSameAs(asMysqlReturnsIt);

    note.useRemoteEmojis(List.of(new CustomEmoji("neoghost", "https://m.example/f.png")));
    assertThat(note.remoteEmojis())
        .containsExactly(new CustomEmoji("neoghost", "https://m.example/f.png"));

    note.useRemoteEmojis(null);
    assertThat(note.getRemoteEmojis()).isNull();
    assertThat(new NoteEntity(1L, "member", null, null).remoteEmojis()).isEmpty();
  }

  @Test
  void aReplyStaysOneWhenItsParentIsGone() {
    NoteEntity reply = new NoteEntity(1L, "답글", 9L, null);
    assertThat(reply.isReply()).isTrue();
    assertThat(reply.isTopLevel()).isFalse();

    ReflectionTestUtils.setField(reply, "inReplyToId", null);
    assertThat(reply.isTopLevel()).isFalse();
    assertThat(new NoteEntity(1L, "새 글", null, null).isTopLevel()).isTrue();
  }
}
