package com.example.short_link.user.application.write.avatar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.example.short_link.common.cache.ProfileCacheInvalidator;
import com.example.short_link.common.storage.ObjectStorage;
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
class UserImagesOutsideTransactionIntegrationTest {

  @MockitoBean private ObjectStorage storage;
  @MockitoSpyBean private ProfileCacheInvalidator profileCache;
  @Autowired private AvatarService avatars;
  @Autowired private BannerService banners;
  @Autowired private UserRepository users;
  @Autowired private JdbcTemplate jdbc;

  private final List<String> calls = new CopyOnWriteArrayList<>();
  private Long userId;
  private String oldAvatar;
  private String oldBanner;

  @BeforeEach
  void aUserWithAnAvatarAndABanner() {
    String name = "img" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    oldAvatar = "avatars/old-" + name + ".jpg";
    oldBanner = "banners/old-" + name + ".jpg";
    UserEntity user = new UserEntity(name + "@example.com", "google", name);
    user.claimUsername(name);
    user.updateAvatar("https://cdn.example.com/" + oldAvatar, oldAvatar);
    user.updateBanner("https://cdn.example.com/" + oldBanner, oldBanner);
    userId = users.save(user).getId();

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
    doAnswer(
            inv -> {
              calls.add("delete " + inv.getArgument(0) + " " + where());
              return null;
            })
        .when(storage)
        .delete(anyString());
  }

  private static String where() {
    return TransactionSynchronizationManager.isActualTransactionActive()
        ? "inside a transaction"
        : "outside";
  }

  @AfterEach
  void deleteTheUser() {
    jdbc.update("DELETE FROM users WHERE id = ?", userId);
  }

  private String column(String name) {
    return jdbc.queryForObject("SELECT " + name + " FROM users WHERE id = ?", String.class, userId);
  }

  @Test
  void replacingAnAvatarAsksStorageOutsideItsTransactionAndDeletesTheOldFileLast() {
    String fresh = "avatars/" + userId + "/new.jpg";

    avatars.commitUpload(userId, fresh);

    assertThat(calls)
        .containsExactly("size outside", "cache outside", "delete " + oldAvatar + " outside");
    assertThat(column("avatar_key")).isEqualTo(fresh);
  }

  @Test
  void replacingABannerAsksStorageOutsideItsTransactionAndDeletesTheOldFileLast() {
    String fresh = "banners/" + userId + "/new.jpg";

    banners.commitUpload(userId, fresh);

    assertThat(calls)
        .containsExactly("size outside", "cache outside", "delete " + oldBanner + " outside");
    assertThat(column("banner_key")).isEqualTo(fresh);
  }

  @Test
  void clearingAnAvatarOrABannerDeletesItsFileOutsideTheTransaction() {
    avatars.clearAvatar(userId);
    banners.clearBanner(userId);

    assertThat(calls)
        .containsExactly("delete " + oldAvatar + " outside", "delete " + oldBanner + " outside");
    assertThat(column("avatar_key")).isNull();
    assertThat(column("banner_key")).isNull();
  }

  @Test
  void aReplacementWhoseTransactionFailsKeepsTheOldFileItStillPointsAt() {
    doThrow(new IllegalStateException("the transaction fails after the swap"))
        .when(profileCache)
        .evictByUsername(anyString());

    assertThatThrownBy(() -> avatars.commitUpload(userId, "avatars/" + userId + "/new.jpg"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> banners.clearBanner(userId)).isInstanceOf(IllegalStateException.class);

    assertThat(calls).noneMatch(call -> call.startsWith("delete"));
    assertThat(column("avatar_key")).isEqualTo(oldAvatar);
    assertThat(column("banner_key")).isEqualTo(oldBanner);
  }
}
