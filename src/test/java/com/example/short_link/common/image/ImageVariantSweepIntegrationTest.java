package com.example.short_link.common.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.example.short_link.note.domain.NoteEntity;
import com.example.short_link.note.domain.NoteMediaEntity;
import com.example.short_link.note.domain.repository.NoteMediaRepository;
import com.example.short_link.note.domain.repository.NoteRepository;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "short-link.image-variants.backfill-enabled=true")
@ActiveProfiles("test")
@Transactional
class ImageVariantSweepIntegrationTest {

  @MockitoBean private ImageVariantQueue queue;
  @Autowired private ImageVariantSweep sweep;
  @Autowired private ImageVariantLedger ledger;
  @Autowired private UserRepository users;
  @Autowired private NoteRepository notes;
  @Autowired private NoteMediaRepository media;
  @Autowired private PostRepository posts;
  @Autowired private JdbcTemplate jdbc;

  private final List<String> queued = new ArrayList<>();
  private String notePhoto;
  private String doneNotePhoto;
  private String bodyPhoto;
  private String markdownPhoto;
  private String cover;

  @BeforeEach
  void originalsInEveryPlaceTheyAreReferenced() {
    when(queue.enabled()).thenReturn(true);
    when(queue.idle()).thenReturn(true);
    when(queue.enqueue(anyString()))
        .thenAnswer(
            call -> {
              queued.add(call.getArgument(0));
              return true;
            });
    String handle = "iv" + UUID.randomUUID().toString().substring(0, 8);
    UserEntity user = users.save(new UserEntity(handle + "@x.com", "google", "g-" + handle));
    long userId = user.getId();
    notePhoto = "note-images/" + userId + "/" + UUID.randomUUID() + ".jpg";
    doneNotePhoto = "note-images/" + userId + "/" + UUID.randomUUID() + ".png";
    long noteId = notes.save(new NoteEntity(userId, "two photos", null, null)).getId();
    media.saveAll(
        List.of(
            new NoteMediaEntity(
                noteId, 0, notePhoto, "https://cdn/" + notePhoto, "image/jpeg", null),
            new NoteMediaEntity(
                noteId, 1, doneNotePhoto, "https://cdn/" + doneNotePhoto, "image/png", null),
            new NoteMediaEntity(
                noteId,
                2,
                "note-images/" + userId + "/moving.gif",
                "https://cdn/moving.gif",
                "image/gif",
                null)));
    ledger.record(doneNotePhoto, ImageVariantLedger.Outcome.MADE);

    String dir = "post-images/" + userId + "/" + UUID.randomUUID() + "/";
    bodyPhoto = dir + UUID.randomUUID() + ".jpeg";
    markdownPhoto = dir + UUID.randomUUID() + ".png";
    cover = dir + UUID.randomUUID() + ".jpg";
    PostEntity draft = new PostEntity(userId, "iv-" + handle, "Photos", "ko");
    draft.updateOgImage("https://cdn.kurl.me/" + cover, cover, true);
    PostEntity post = posts.save(draft);
    block(post.getId(), "IMAGE", "{\"url\":\"https://cdn.kurl.me/" + bodyPhoto + "\"}", 0);
    block(
        post.getId(),
        "PARAGRAPH",
        "before ![x](https://cdn.kurl.me/" + markdownPhoto + ") and https://elsewhere/a.jpg",
        1);
  }

  @Test
  void theRecentSweepFindsOriginalsWithoutVariantsInNotesBodiesAndCovers() {
    sweep.sweepRecent();

    assertThat(queued).contains(notePhoto, bodyPhoto, markdownPhoto, cover);
    assertThat(queued).doesNotContain(doneNotePhoto);
    assertThat(queued).noneMatch(key -> key.endsWith(".gif"));
  }

  @Test
  void theBackfillReachesTheSameOriginalsOnePerStep() {
    List<String> wanted = List.of(notePhoto, bodyPhoto, markdownPhoto, cover);
    for (int step = 0; step < 200_000 && !queued.containsAll(wanted); step++) {
      sweep.backfillStep();
    }

    assertThat(queued).containsAll(wanted);
    assertThat(queued).doesNotContain(doneNotePhoto);
    assertThat(queued).doesNotHaveDuplicates();
  }

  private void block(long postId, String type, String content, int order) {
    Timestamp now = Timestamp.from(Instant.now());
    jdbc.update(
        "INSERT INTO post_block (post_id, block_type, content, block_order, created_at, updated_at)"
            + " VALUES (?, ?, ?, ?, ?, ?)",
        postId,
        type,
        content,
        order,
        now,
        now);
  }
}
