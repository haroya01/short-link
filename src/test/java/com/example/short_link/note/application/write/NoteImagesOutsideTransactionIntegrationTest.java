package com.example.short_link.note.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.example.short_link.common.storage.ObjectStorage;
import com.example.short_link.note.domain.NoteScheduleEntity;
import com.example.short_link.note.domain.repository.NoteScheduleRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

// Not @Transactional: the test asks whether a transaction is open while storage is called.
@SpringBootTest
@ActiveProfiles("test")
class NoteImagesOutsideTransactionIntegrationTest {

  @MockitoBean private ObjectStorage storage;
  @Autowired private NoteComposer composer;
  @Autowired private NoteScheduleService schedules;
  @Autowired private NoteScheduleRepository scheduled;
  @Autowired private UserRepository users;
  @Autowired private JsonMapper json;
  @Autowired private JdbcTemplate jdbc;

  private final List<String> calls = new CopyOnWriteArrayList<>();
  private Long writer;

  @BeforeEach
  void storageThatNotesWhetherATransactionIsOpen() {
    String name = "s3" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    UserEntity user = new UserEntity(name + "@example.com", "google", name);
    user.claimUsername(name);
    writer = users.save(user).getId();
    when(storage.isConfigured()).thenReturn(true);
    when(storage.objectSize(anyString()))
        .thenAnswer(
            inv -> {
              calls.add("size " + where());
              return Optional.of(4L);
            });
    doAnswer(
            inv -> {
              calls.add("cache " + where());
              return null;
            })
        .when(storage)
        .applyImmutableCacheControl(anyString());
  }

  private static String where() {
    return TransactionSynchronizationManager.isActualTransactionActive()
        ? "inside a transaction"
        : "outside";
  }

  @AfterEach
  void deleteCommittedRows() {
    jdbc.update(
        "DELETE m FROM note_media m JOIN note n ON n.id = m.note_id WHERE n.user_id = ?", writer);
    jdbc.update(
        "DELETE t FROM note_tag t JOIN note n ON n.id = t.note_id WHERE n.user_id = ?", writer);
    jdbc.update("DELETE FROM note WHERE user_id = ? AND in_reply_to_id IS NOT NULL", writer);
    jdbc.update("DELETE FROM note WHERE user_id = ?", writer);
    jdbc.update("DELETE FROM note_schedule WHERE user_id = ?", writer);
    jdbc.update("DELETE FROM users WHERE id = ?", writer);
  }

  private NoteDraft withImage(String body, String name) {
    return new NoteDraft(
        body,
        List.of(new NoteDraft.Image("note-images/" + writer + "/" + name + ".png", null)),
        null,
        null);
  }

  @Test
  void aNoteAsksStorageAboutItsImagesBeforeItsTransactionOpens() {
    composer.create(
        writer,
        new NoteDraft(
            "두 장",
            List.of(
                new NoteDraft.Image("note-images/" + writer + "/a.png", null),
                new NoteDraft.Image("note-images/" + writer + "/b.png", null)),
            null,
            null));

    assertThat(calls)
        .containsExactly("size outside", "cache outside", "size outside", "cache outside");
  }

  @Test
  void aThreadAsksStorageAboutEveryPartsImagesBeforeItsTransactionOpens() {
    composer.createThread(writer, List.of(withImage("하나", "a"), withImage("둘", "b")));

    assertThat(calls)
        .containsExactly("size outside", "cache outside", "size outside", "cache outside");
  }

  @Test
  void schedulingAndPublishingANoteAskStorageOutsideTheirTransactions() {
    schedules.schedule(writer, withImage("나중에", "a"), Instant.now().plus(Duration.ofHours(1)));
    scheduled.save(
        new NoteScheduleEntity(
            writer,
            Instant.now().minus(Duration.ofMinutes(1)),
            json.writeValueAsString(withImage("지금", "b"))));

    schedules.publishDue();

    assertThat(calls).isNotEmpty().allMatch(call -> call.endsWith("outside"));
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM note WHERE user_id = ? AND body = '지금'", Long.class, writer))
        .isEqualTo(1);
  }
}
