package com.example.short_link.note.application.read;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.note.domain.NoteAuthor;
import com.example.short_link.note.domain.QuotedPost;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoteViewPartsTest {

  private static final NoteAuthor WRITER = new NoteAuthor(7L, "me", "https://cdn/me.png");
  private static final NoteAuthor REPOSTER = new NoteAuthor(9L, "mina", null);
  private static final NoteAuthor QUOTED_WRITER = new NoteAuthor(11L, "yuki", null);
  private static final Instant WRITTEN = Instant.parse("2026-10-27T03:00:00Z");
  private static final Instant EDITED = Instant.parse("2026-10-27T04:00:00Z");
  private static final NoteView.Remote REMOTE =
      new NoteView.Remote("ARTICLE", "A long read", "https://m.example/@alice/1", "books");
  private static final NoteView.Room ROOM = new NoteView.Room(30L, "책 모임");
  private static final List<NoteView.Media> MEDIA =
      List.of(new NoteView.Media("https://cdn/a.png", "창밖", "image/png", 1200, 900));
  private static final QuotedPost QUOTED_POST = new QuotedPost(5L, "Title", "slug", "yuki", 11L);
  private static final NoteView.QuotedNote QUOTED_NOTE =
      new NoteView.QuotedNote(10L, "인용", WRITTEN, QUOTED_WRITER, List.of(), "cw", true);
  private static final NoteView.LinkPreview CARD =
      new NoteView.LinkPreview("https://example.com/a", "Example", "A page", "https://e/a.png");
  private static final NoteView.Poll POLL =
      new NoteView.Poll(EDITED, false, true, 4, 3, List.of(), true, List.of(1));
  private static final NoteView.SelfThread THREAD = new NoteView.SelfThread(3, List.of());

  private static final NoteView.Origin ORIGIN =
      new NoteView.Origin(1L, WRITER, WRITTEN, EDITED, REMOTE);
  private static final NoteView.Text TEXT =
      new NoteView.Text("@mina 보세요", List.of("mina"), "스포일러", true, "ko");
  private static final NoteView.Reach REACH = new NoteView.Reach("followers", "mentioned", ROOM);
  private static final NoteView.Standing STANDING = new NoteView.Standing(50L, true, false);
  private static final NoteView.Attached ATTACHED =
      new NoteView.Attached(MEDIA, QUOTED_POST, QUOTED_NOTE, CARD, POLL);
  private static final NoteView.Counts COUNTS = new NoteView.Counts(3L, 4, 5L, 6);
  private static final NoteView.Mine MINE = new NoteView.Mine(true, false, true, false, true);
  private static final NoteView.InFeed IN_FEED = new NoteView.InFeed(REPOSTER, THREAD);

  private static NoteView composed(NoteView.InFeed inFeed) {
    return NoteView.compose(ORIGIN, TEXT, REACH, STANDING, ATTACHED, COUNTS, MINE, inFeed);
  }

  @Test
  void eachPartLandsInTheFieldsItNames() {
    assertThat(composed(IN_FEED))
        .isEqualTo(
            new NoteView(
                1L,
                "@mina 보세요",
                WRITTEN,
                EDITED,
                3L,
                true,
                WRITER,
                MEDIA,
                QUOTED_POST,
                50L,
                4,
                5L,
                false,
                QUOTED_NOTE,
                CARD,
                REPOSTER,
                6,
                true,
                List.of("mina"),
                "스포일러",
                true,
                true,
                "followers",
                POLL,
                false,
                "ko",
                THREAD,
                "mentioned",
                true,
                false,
                REMOTE,
                ROOM));
  }

  @Test
  void anAnonymousReaderHasNoMarksAndANoteAloneHasNoFeedPlace() {
    NoteView view =
        NoteView.compose(
            ORIGIN,
            TEXT,
            REACH,
            STANDING,
            ATTACHED,
            COUNTS,
            NoteView.Mine.ANONYMOUS,
            NoteView.InFeed.ALONE);

    assertThat(view.likedByMe()).isNull();
    assertThat(view.repostedByMe()).isNull();
    assertThat(view.bookmarkedByMe()).isNull();
    assertThat(view.conversationMuted()).isNull();
    assertThat(view.canReply()).isNull();
    assertThat(view.repostedBy()).isNull();
    assertThat(view.thread()).isNull();
  }

  @Test
  void showingANoteInAFeedChangesOnlyWhereItShows() {
    NoteView alone = composed(NoteView.InFeed.ALONE);
    NoteAuthor other = new NoteAuthor(12L, "sora", null);
    NoteView.SelfThread parts = new NoteView.SelfThread(2, List.of(alone));

    assertThat(alone.withRepostedBy(other)).isEqualTo(composed(new NoteView.InFeed(other, null)));
    assertThat(alone.withThread(parts)).isEqualTo(composed(new NoteView.InFeed(null, parts)));
    assertThat(composed(IN_FEED).withRepostedBy(other))
        .isEqualTo(composed(new NoteView.InFeed(other, THREAD)));
    assertThat(composed(IN_FEED).withThread(parts))
        .isEqualTo(composed(new NoteView.InFeed(REPOSTER, parts)));
  }
}
