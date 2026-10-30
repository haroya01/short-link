package com.example.short_link.event.application.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.example.short_link.common.storage.ObjectStorage;
import com.example.short_link.event.domain.ContactField;
import com.example.short_link.event.domain.EventEntity;
import com.example.short_link.event.domain.repository.EventRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
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

// Not @Transactional: the test asks whether a transaction is open while storage is called.
@SpringBootTest
@ActiveProfiles("test")
class EventCoverOutsideTransactionIntegrationTest {

  @MockitoBean private ObjectStorage storage;
  @Autowired private EventCoverImageService covers;
  @Autowired private EventRepository events;
  @Autowired private UserRepository users;
  @Autowired private JdbcTemplate jdbc;

  private final List<String> calls = new CopyOnWriteArrayList<>();
  private Long userId;
  private Long eventId;

  @BeforeEach
  void anEventItsOwnerIsAddingACoverTo() {
    String name = "cov" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    UserEntity user = new UserEntity(name + "@example.com", "google", name);
    user.claimUsername(name);
    userId = users.save(user).getId();
    eventId =
        events
            .save(
                new EventEntity(
                    userId,
                    name.substring(0, 13),
                    "스터디",
                    null,
                    Instant.parse("2027-01-01T10:00:00Z"),
                    null,
                    "Asia/Seoul",
                    null,
                    null,
                    null,
                    null,
                    null,
                    ContactField.EMAIL))
            .getId();

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
  void deleteTheEventAndItsOwner() {
    jdbc.update("DELETE FROM event WHERE id = ?", eventId);
    jdbc.update("DELETE FROM users WHERE id = ?", userId);
  }

  @Test
  void aCoverAsksStorageAboutItsFileOutsideTheTransactionThatStoresIt() {
    String key = "event-covers/" + userId + "/" + eventId + "/cover.jpg";

    covers.commitUpload(userId, eventId, key);

    assertThat(calls).containsExactly("size outside", "cache outside");
    assertThat(
            jdbc.queryForObject(
                "SELECT cover_image_key FROM event WHERE id = ?", String.class, eventId))
        .isEqualTo(key);
  }
}
