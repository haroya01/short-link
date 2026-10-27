package com.example.short_link.federation.application.trends;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.note.application.read.NoteViewFixtures;
import com.example.short_link.note.domain.NoteAuthor;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class TrendingNoteShapeTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private static Set<String> keys(JsonNode node) {
    return new LinkedHashSet<>(node.propertyNames());
  }

  private static TrendingNote trending() {
    return new TrendingNote(
        "hi",
        Instant.parse("2026-10-27T00:00:00Z"),
        null,
        1,
        2,
        3,
        new TrendingNote.Author(null, "alice@m.example", null, "Alice", 4L, "https://m.example/@a"),
        List.of(),
        null,
        null,
        false,
        "ko",
        null,
        new TrendingNote.Remote("NOTE", null, "https://m.example/@a/1", null),
        "https://m.example/users/a/statuses/1",
        "https://m.example/@a/1",
        List.of("m.example"));
  }

  @Test
  void aTrendingPostCarriesEveryKeyOfANoteCardButTheRoom() {
    JsonNode note =
        JSON.valueToTree(
            NoteViewFixtures.note(
                1L, "hi", NoteAuthor.remote(4L, "alice@m.example", null, "Alice", null), null, 0L));
    JsonNode trending = JSON.valueToTree(trending());

    Set<String> missing = keys(note);
    missing.removeAll(keys(trending));
    assertThat(missing).containsExactly("room");
    assertThat(keys(trending.get("author"))).containsAll(keys(note.get("author")));
  }
}
