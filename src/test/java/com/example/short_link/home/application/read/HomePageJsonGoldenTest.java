package com.example.short_link.home.application.read;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.common.reply.ReplyPreview;
import com.example.short_link.federation.application.trends.RemoteTrendingFeed;
import com.example.short_link.note.application.read.NoteView;
import com.example.short_link.note.application.read.NoteViewFixtures;
import com.example.short_link.note.domain.NoteAuthor;
import com.example.short_link.post.application.read.PostCard;
import com.example.short_link.post.application.read.PublicAuthorView;
import com.example.short_link.post.application.read.PublicFeedItem;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

class HomePageJsonGoldenTest {

  private static final Path GOLDEN = Path.of("src/test/resources/home-page-golden");
  private static final JsonMapper JSON =
      JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
  private static final NoteAuthor MINA = new NoteAuthor(9L, "mina", null);

  private static NoteView note(long id) {
    return NoteViewFixtures.note(id, "노트 " + id, MINA, null, 3L);
  }

  private static PostCard post(long id) {
    return new PostCard(
        new PublicFeedItem(
            id,
            new PublicAuthorView(9L, "mina", null, "https://cdn/mina.png"),
            "slug-" + id,
            "Title " + id,
            "excerpt " + id,
            null,
            null,
            "ko",
            List.of("산책"),
            Instant.parse("2026-10-20T00:00:00Z"),
            3,
            4),
        2,
        5,
        true);
  }

  @Test
  void aHomePageReadsAsItDidForEveryKindAndBothEnds() throws IOException {
    HomeFeedQueryService.Page scrolled =
        new HomeFeedQueryService.Page(
            List.of(
                new HomeFeedQueryService.Item("note", note(1L), null, null),
                new HomeFeedQueryService.Item("post", null, post(5L), null),
                new HomeFeedQueryService.Item(
                    "remote",
                    null,
                    null,
                    new RemoteTrendingFeed.KeptNote(note(2L), "https://m.example/notes/2"))),
            "h1792404000123.1.1");
    HomeFeedQueryService.Page refreshed =
        new HomeFeedQueryService.Page(
            List.of(new HomeFeedQueryService.Item("note", note(3L), null, null)),
            null,
            "3.5",
            false);

    assertGolden("home.json", List.of(scrolled, refreshed));
  }

  @Test
  void aRoomPageReadsAsItDidWithEachItemsReplies() throws IOException {
    RoomItemsQueryService.Page page =
        new RoomItemsQueryService.Page(
            List.of(
                new RoomItemsQueryService.RoomItem(
                    "note",
                    note(1L),
                    null,
                    List.of(
                        new RoomItemsQueryService.Reply(
                            11L, new ReplyPreview.Author(9L, "mina", "Mina", null), "답글"))),
                new RoomItemsQueryService.RoomItem("post", null, post(5L), List.of())),
            "1.1");

    assertGolden("room.json", List.of(page));
  }

  private static void assertGolden(String name, Object pages) throws IOException {
    String actual = JSON.writeValueAsString(pages) + "\n";
    assertThat(actual).isEqualTo(Files.readString(GOLDEN.resolve(name), StandardCharsets.UTF_8));
  }
}
