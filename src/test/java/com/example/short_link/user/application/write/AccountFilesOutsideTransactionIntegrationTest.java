package com.example.short_link.user.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.example.short_link.common.storage.ObjectStorage;
import com.example.short_link.common.storage.UserFolder;
import com.example.short_link.link.application.LinkAccountData;
import com.example.short_link.note.application.write.NoteComposer;
import com.example.short_link.note.application.write.NoteDraft;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// Not @Transactional: the test asks whether a transaction is open while storage is called.
@SpringBootTest
@ActiveProfiles("test")
class AccountFilesOutsideTransactionIntegrationTest {

  @MockitoBean private ObjectStorage storage;
  @MockitoSpyBean private LinkAccountData linkAccountData;
  @Autowired private NoteComposer composer;
  @Autowired private UserDeletionService deletion;
  @Autowired private UserRepository users;
  @Autowired private JdbcTemplate jdbc;

  private final List<String> calls = new CopyOnWriteArrayList<>();
  private Long userId;
  private String image;

  @BeforeEach
  void aUserWithAnImageInANote() {
    String name = "del" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    UserEntity user = new UserEntity(name + "@example.com", "google", name);
    user.claimUsername(name);
    userId = users.save(user).getId();
    image = "note-images/" + userId + "/a.png";

    when(storage.isConfigured()).thenReturn(true);
    when(storage.objectSize(anyString())).thenReturn(Optional.of(4L));
    composer.create(
        userId, new NoteDraft("사진 한 장", List.of(new NoteDraft.Image(image, null)), null, null));
    doAnswer(
            inv -> {
              calls.add("delete " + inv.getArgument(0) + " " + where());
              return null;
            })
        .when(storage)
        .deleteFolder(anyString());
  }

  private static String where() {
    return TransactionSynchronizationManager.isActualTransactionActive()
        ? "inside a transaction"
        : "outside";
  }

  @AfterEach
  void deleteWhatIsLeft() {
    jdbc.update(
        "DELETE m FROM note_media m JOIN note n ON n.id = m.note_id WHERE n.user_id = ?", userId);
    jdbc.update(
        "DELETE t FROM note_tag t JOIN note n ON n.id = t.note_id WHERE n.user_id = ?", userId);
    jdbc.update("DELETE FROM note WHERE user_id = ?", userId);
    jdbc.update("DELETE FROM users WHERE id = ?", userId);
  }

  private long imagesStillStored() {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM note_media m JOIN note n ON n.id = m.note_id WHERE n.user_id = ?",
        Long.class,
        userId);
  }

  @Test
  void aHardDeleteEmptiesTheUsersFoldersOutsideTheTransactionThatErasedTheirRows() {
    deletion.hardDelete(userId);

    assertThat(calls)
        .contains("delete note-images/" + userId + "/ outside")
        .hasSize(UserFolder.values().length)
        .allMatch(call -> call.endsWith(" outside"));
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id = ?", Long.class, userId))
        .isZero();
    assertThat(imagesStillStored()).isZero();
  }

  @Test
  void aHardDeleteThatRollsBackKeepsTheImagesItsRowsStillPointAt() {
    doThrow(new IllegalStateException("the purge fails after the note eraser"))
        .when(linkAccountData)
        .eraseOwned(userId);

    assertThatThrownBy(() -> deletion.hardDelete(userId)).isInstanceOf(IllegalStateException.class);

    assertThat(calls).isEmpty();
    assertThat(imagesStillStored()).isEqualTo(1);
  }
}
