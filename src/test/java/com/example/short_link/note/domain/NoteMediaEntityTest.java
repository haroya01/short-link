package com.example.short_link.note.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.note.application.read.NoteView;
import org.junit.jupiter.api.Test;

class NoteMediaEntityTest {

  @Test
  void aSizeIsKeptOnlyAsAPairOfSaneSides() {
    assertThat(sized(1200, 900)).containsExactly(1200, 900);
    assertThat(sized(1, NoteMediaEntity.MAX_DIMENSION))
        .containsExactly(1, NoteMediaEntity.MAX_DIMENSION);
    assertThat(sized(1200, null)).containsExactly(null, null);
    assertThat(sized(null, 900)).containsExactly(null, null);
    assertThat(sized(0, 900)).containsExactly(null, null);
    assertThat(sized(1200, NoteMediaEntity.MAX_DIMENSION + 1)).containsExactly(null, null);
    assertThat(sized(-5, 900)).containsExactly(null, null);
  }

  @Test
  void theViewCarriesTheSize() {
    NoteView.Media view =
        NoteView.Media.of(
            new NoteMediaEntity(1L, 0, "k", "https://cdn/k", "image/png", "alt", 640, 480));

    assertThat(view).isEqualTo(new NoteView.Media("https://cdn/k", "alt", "image/png", 640, 480));
    assertThat(
            NoteView.Media.of(new NoteMediaEntity(1L, 0, "k", "https://cdn/k", "image/png", null)))
        .isEqualTo(new NoteView.Media("https://cdn/k", null, "image/png", null, null));
  }

  private static Integer[] sized(Integer width, Integer height) {
    NoteMediaEntity image =
        new NoteMediaEntity(1L, 0, "k", "https://cdn/k", "image/png", null, width, height);
    return new Integer[] {image.getWidth(), image.getHeight()};
  }
}
