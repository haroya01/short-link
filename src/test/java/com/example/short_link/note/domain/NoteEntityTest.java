package com.example.short_link.note.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

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
}
